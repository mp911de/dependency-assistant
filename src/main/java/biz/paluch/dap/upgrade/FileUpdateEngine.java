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

/**
 * Applies dependency updates to physical build files or PSI preview copies.
 *
 * <p>For physical-file mutation, callers provide the write action and any IDE
 * command or undo boundary. When the file has an associated {@link Document},
 * the engine commits the document before and after applying updates and saves
 * it afterward. Preview applies to a caller-provided PSI copy without
 * committing or saving a document.
 *
 * @author Mark Paluch
 */
public class FileUpdateEngine {

	private final Project project;

	private final UpdateFunction updateFunction;

	private final BetterPsiManager psiManager;

	private final PsiDocumentManager documentManager;

	private final FileDocumentManager fileDocumentManager;

	/**
	 * Create an engine that routes each source file through all registered
	 * dependency assistants.
	 *
	 * @param project the project whose build files are updated.
	 */
	public FileUpdateEngine(Project project) {
		this(project, assistants(project));
	}

	/**
	 * Create an engine that routes source files through the given dependency
	 * assistant.
	 *
	 * @param project the project whose build files are updated.
	 * @param assistant the assistant used to recognize and update files.
	 */
	public FileUpdateEngine(Project project, DependencyAssistant assistant) {
		this(project, new DependencyAssistantsUpdateFunction(assistant));
	}

	/**
	 * Create an engine using the given update function.
	 *
	 * @param project the project whose build files are updated.
	 * @param updateFunction the function that applies updates to a resolved PSI
	 * file or preview copy.
	 */
	public FileUpdateEngine(Project project, UpdateFunction updateFunction) {
		this.project = project;
		this.updateFunction = updateFunction;
		this.psiManager = BetterPsiManager.getInstance(project);
		this.documentManager = PsiDocumentManager.getInstance(project);
		this.fileDocumentManager = FileDocumentManager.getInstance();
	}

	/**
	 * Create an update function that applies through every registered assistant
	 * that supports the source file and produces an available context.
	 *
	 * @param project the project.
	 * @return the update function.
	 */
	public static UpdateFunction assistants(Project project) {
		return new DependencyAssistantsUpdateFunction(
				DependencyAssistantDispatcher.findAll(project));
	}

	/**
	 * Create an update function that applies every target through the given project
	 * dependency context without inspecting the source file.
	 *
	 * @param context the project dependency context.
	 * @return the update function.
	 */
	public static UpdateFunction context(ProjectDependencyContext context) {
		return new ProjectDependencyContextUpdateFunction(context);
	}

	/**
	 * Apply every update to each resolved file in the scope.
	 *
	 * <p>The callback is invoked after each update that changes target file text.
	 * Missing paths retained by the scope are not processed. Empty updates or a
	 * scope with no resolved files produce a zero-change result.
	 *
	 * @param scope the resolved build files to update.
	 * @param updates the dependency updates to apply to every file.
	 * @param afterApply the callback for each update that changes a file.
	 * @return the number of update steps that changed file text across the scope.
	 * @throws IllegalStateException if a scoped file cannot be resolved to PSI.
	 */
	public UpgradeResult apply(FileScope scope, List<DependencyUpdate> updates, Consumer<DependencyUpdate> afterApply) {

		ApplicationManager.getApplication().assertWriteAccessAllowed();

		if (updates.isEmpty() || scope.isEmpty()) {
			return UpgradeResult.none();
		}

		UpgradeResult applied = UpgradeResult.none();
		for (VirtualFile file : scope.toList()) {
			applied = applied.merge(applyUpdates(file, updates, afterApply));
		}

		return applied;
	}

	/**
	 * Apply the supplied update sequence to a physical file.
	 *
	 * <p>The sequence controls its own post-update callbacks. This method does not
	 * perform change tracking.
	 *
	 * @param file the physical build file to update.
	 * @param updates the update sequence to apply.
	 * @throws IllegalStateException if the file cannot be resolved to PSI.
	 */
	public void applyUpdates(VirtualFile file, DependencyUpdates updates) {
		doWithFile(file, psiFile -> {
			updateFunction.apply(psiFile, psiFile, updates);
			return null;
		});
	}

	/**
	 * Apply the given updates to a physical file and report updates that change its
	 * text.
	 *
	 * @param file the physical build file to update.
	 * @param updates the dependency updates to apply.
	 * @param afterApply the callback for each update that changes the file.
	 * @return the number of update steps that changed file text.
	 * @throws IllegalStateException if the file cannot be resolved to PSI.
	 */
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
	 * Apply updates using one PSI file for assistant dispatch and another as the
	 * mutation target.
	 *
	 * <p>This split lets preview use a non-physical target copy. The physical-file
	 * methods pass the same PSI file as source and target. No document is committed
	 * or saved by this method.
	 *
	 * @param source the source file used for assistant recognition and context
	 * creation.
	 * @param target the PSI file to mutate.
	 * @param updates the dependency updates to apply.
	 */
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
	 * Tracks update steps that change a {@link PsiFile}'s text between successive
	 * observations.
	 */
	public static class ChangeTracker {

		private int changeCount;

		private String text;

		private ChangeTracker(String text) {
			this.text = text;
		}

		/**
		 * Create a tracker initialized with the file's current text.
		 *
		 * @param psiFile the file to track.
		 * @return a tracker initialized with the current file text.
		 */
		public static ChangeTracker of(PsiFile psiFile) {
			return new ChangeTracker(psiFile.getText());
		}

		/**
		 * Compare the file with the previous observation and advance the snapshot.
		 *
		 * <p>The change count advances only when the text differs.
		 *
		 * @param file the file after an update step.
		 * @return {@literal true} if the text changed since the previous observation;
		 * {@literal false} otherwise.
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

		/**
		 * Return the accumulated number of changed update steps.
		 *
		 * @return the accumulated change result.
		 */
		public UpgradeResult getChanges() {
			return UpgradeResult.of(changeCount);
		}

	}

	/**
	 * Strategy for applying dependency updates to a PSI target selected from a
	 * source file.
	 */
	@FunctionalInterface
	public interface UpdateFunction {

		/**
		 * Apply updates to the target file.
		 *
		 * @param source the source used for integration selection and context.
		 * @param target the file to mutate.
		 * @param updates the updates to apply.
		 */
		void apply(PsiFile source, PsiFile target, DependencyUpdates updates);

	}

	/**
	 * Routes a source through each supporting dependency assistant and applies
	 * updates through every available context.
	 */
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

	/**
	 * Applies updates through one fixed project dependency context.
	 */
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
