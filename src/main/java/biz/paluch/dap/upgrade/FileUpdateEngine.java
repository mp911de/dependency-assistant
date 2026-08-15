/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.upgrade;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;

/**
 * File-mutation engine.
 * <p>File writes are expected to be inside the caller's write action. Methods
 * do not create IDE commands or undo boundaries is created. Changed documents
 * are saved, so the caller's write action must be held on the EDT.
 *
 * @author Mark Paluch
 */
public class FileUpdateEngine {

	private final Project project;

	private final UpdateFunction updateFunction;

	private final BetterPsiManager psiManager;

	private final PsiDocumentManager documentManager;

	private final FileDocumentManager fileDocumentManager;

	public FileUpdateEngine(Project project) {
		this(project, assistants(project));
	}

	public FileUpdateEngine(Project project, DependencyAssistant assistant) {
		this(project, new DependencyAssistantsUpdateFunction(assistant));
	}

	public FileUpdateEngine(Project project, UpdateFunction updateFunction) {
		this.project = project;
		this.updateFunction = updateFunction;
		this.psiManager = BetterPsiManager.getInstance(project);
		this.documentManager = PsiDocumentManager.getInstance(project);
		this.fileDocumentManager = FileDocumentManager.getInstance();
	}

	/**
	 * Create an update function that applies updates to all registered assistants.
	 * @param project the project.
	 * @return the update function.
	 */
	public static UpdateFunction assistants(Project project) {
		return new DependencyAssistantsUpdateFunction(
				DependencyAssistantDispatcher.findAll(project));
	}

	/**
	 * Create an update function that applies updates to all registered assistants.
	 * @param context the project dependency context.
	 * @return the update function.
	 */
	public static UpdateFunction context(ProjectDependencyContext context) {
		return new ProjectDependencyContextUpdateFunction(context);
	}

	@RequiresWriteLock
	public UpgradeResult apply(FileScope scope, List<DependencyUpdate> updates, Consumer<DependencyUpdate> afterApply) {

		ApplicationManager.getApplication().assertWriteAccessAllowed();

		if (updates.isEmpty() || scope.isEmpty()) {
			return UpgradeResult.none();
		}

		UpgradeResult applied = UpgradeResult.none();
		for (VirtualFile file : scope.toList()) {
			applied.merge(applyUpdates(file, updates, afterApply));
		}

		return applied;
	}

	public void applyUpdates(VirtualFile file, DependencyUpdates updates) {
		doWithFile(file, psiFile -> {
			updateFunction.apply(psiFile, psiFile, updates);
			return null;
		});
	}

	@RequiresWriteLock
	public UpgradeResult applyUpdates(VirtualFile file, List<DependencyUpdate> updates,
			Consumer<DependencyUpdate> afterApply) {
		return doWithFile(file, psiFile -> apply(psiFile, psiFile, updates, afterApply));
	}

	private <T> T doWithFile(VirtualFile file, Function<PsiFile, T> updateFunction) {
		Document document = fileDocumentManager.getDocument(file);
		if (document != null) {
			documentManager.commitDocument(document);
		}

		PsiFile psiFile = psiManager.findFile(file);
		if (psiFile == null) {
			throw new IllegalStateException("Cannot resolve file '%s'".formatted(file.getPresentableUrl()));
		}

		T result = updateFunction.apply(psiFile);

		if (document != null) {
			documentManager.commitDocument(document);
			fileDocumentManager.saveDocument(document);
		}
		return result;
	}

	/**
	 * Preview uses a non-physical target copy while real apply uses the source as
	 * target.
	 */
	@RequiresWriteLock
	public void applyToFile(PsiFile source, PsiFile target, List<DependencyUpdate> updates) {
		if (updates.isEmpty()) {
			return;
		}
		apply(source, target, updates, update -> {
		});
	}

	private UpgradeResult apply(PsiFile source, PsiFile target, List<DependencyUpdate> updates,
			Consumer<DependencyUpdate> afterApply) {

		ChangeTracker tracker = ChangeTracker.of(target);
		DependencyUpdates dependencyUpdates = new DependencyUpdates(updates) {

			@Override
			public void afterDependencyUpdate(PsiFile file, DependencyUpdate update) {
				if (tracker.update(target)) {
					afterApply.accept(update);
				}
			}

		};

		updateFunction.apply(source, target, dependencyUpdates);
		return tracker.getChanges();
	}

	/**
	 * Tracks changes to a {@link PsiFile}.
	 */
	static class ChangeTracker {

		private int changeCount;

		private String text;

		private ChangeTracker(String text) {
			this.text = text;
		}

		/**
		 * Create a new {@link ChangeTracker} for the given {@link PsiFile} and
		 * initialize the text content.
		 * @param psiFile file to track.
		 * @return a new {@code ChangeTracker} instance for the given {@code PsiFile}.
		 */
		public static ChangeTracker of(PsiFile psiFile) {
			return new ChangeTracker(psiFile.getText());
		}

		/**
		 * Update the tracker after applying an update.
		 * <p>Change tracker is only updated if the text content has changed.
		 * @param file the current file.
		 */
		public boolean update(PsiFile file) {
			String after = file.getText();
			boolean changed = !after.equals(this.text);
			if (changed) {
				this.changeCount++;
			}
			this.text = after;
			return changed;
		}

		public UpgradeResult getChanges() {
			return UpgradeResult.of(changeCount);
		}

	}

	@FunctionalInterface
	public interface UpdateFunction {

		void apply(PsiFile source, PsiFile target, DependencyUpdates updates);

	}

	public static class DependencyAssistantsUpdateFunction implements UpdateFunction {

		private final List<DependencyAssistant> assistants;

		public DependencyAssistantsUpdateFunction(DependencyAssistant assistant) {
			this(List.of(assistant));
		}

		public DependencyAssistantsUpdateFunction(List<DependencyAssistant> assistants) {
			this.assistants = assistants;
		}

		@Override
		public void apply(PsiFile source, PsiFile file, DependencyUpdates updates) {

			for (DependencyAssistant assistant : assistants) {
				if (!assistant.supports(source)) {
					continue;
				}
				ProjectDependencyContext context = assistant.createContext(source);
				if (!context.isAvailable()) {
					continue;
				}
				context.applyUpdates(file, updates);
			}
		}

	}

	public static class ProjectDependencyContextUpdateFunction implements UpdateFunction {

		private final ProjectDependencyContext context;

		public ProjectDependencyContextUpdateFunction(ProjectDependencyContext context) {
			this.context = context;
		}

		@Override
		public void apply(PsiFile source, PsiFile target, DependencyUpdates updates) {
			context.applyUpdates(target, updates);
		}

	}

}
