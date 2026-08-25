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

package biz.paluch.dap.assistant.action;

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.assistant.check.DependencyCheck;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.assistant.review.DependencyCheckDialog;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Cancellable background task that resolves an {@link UpgradeScope} from an
 * {@link UpgradeRequest} and runs a {@link DependencyCheck} over it.
 *
 * <p>Scope resolution waits for smart mode and runs in a non-blocking read
 * action. An empty scope produces an informational notification without running
 * the check. A completed check with no candidates reports that no updates are
 * available; otherwise the task opens a {@link DependencyCheckDialog} and
 * selects the requested focus artifact, if any.
 *
 * <p>The task does not modify build files. Direct updates occur only after the
 * user confirms them in the dialog.
 *
 * @author Mark Paluch
 */
public class DependencyCheckTask extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(DependencyCheckTask.class);

	private final Project project;

	private final UpgradeRequest request;

	private volatile @Nullable UpgradeScope scope;

	private volatile @Nullable DependencyCheckResult resultRef;

	public DependencyCheckTask(Project project, UpgradeRequest request) {
		super(project, MessageBundle.message("action.check.dependencies.progress"), true);
		this.project = project;
		this.request = request;
	}

	@Override
	public void run(ProgressIndicator indicator) {

		UpgradeScope scope = ReadAction.nonBlocking(() -> {
			if (!request.hasSingleSource()) {
				indicator.setText(MessageBundle.message("action.check.dependencies.resolvingScope"));
			}
			return UpgradeScopeResolver.resolve(project, request);
		}).inSmartMode(project).executeSynchronously();

		this.scope = scope;

		if (scope.isEmpty()) {
			indicator.stop();
			return;
		}

		DependencyCheck dependencyCheck = new DependencyCheck(project);
		indicator.setText(MessageBundle.message("action.check.dependencies.progress"));
		this.resultRef = dependencyCheck.findDependencyUpgrades(indicator, scope);
	}

	@Override
	public void onSuccess() {

		UpgradeScope scope = this.scope;
		if (scope != null) {
			if (scope.isEmpty()) {
				notifyNotFound(scope.reason());
				return;
			}

			showResult(scope);
		}
	}

	private void showResult(UpgradeScope scope) {

		DependencyCheckResult result = resultRef;
		if (result == null || result.isEmpty()) {
			Notifications.info(project, MessageBundle.message("action.check.dependencies.empty.title"),
					MessageBundle.message("action.check.dependencies.empty.checked", scope.size()));
			return;
		}

		DependencyCheckDialog dialog = new DependencyCheckDialog(project, result, getTitle(scope),
				request.hasEditorFile());
		dialog.show();

		PackageIdentity focusArtifact = request.focusArtifact();
		if (focusArtifact != null) {
			dialog.selectCandidate(focusArtifact);
		}
	}

	@Override
	public void onThrowable(Throwable error) {
		LOG.warn("Dependency check failed", error);

		Notifications.error(project,
				MessageBundle.message("action.check.dependencies.task.error", Notifications.errorMessage(error)));
	}

	private void notifyNotFound(UpgradeScope.Reason reason) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);

		if (assistants.isEmpty()) {
			assistants = DependencyAssistantDispatcher.findAll();
			if (assistants.isEmpty()) {
				Notifications.info(project, MessageBundle.message("action.check.dependencies.no-assistants.title"),
						MessageBundle.message("action.check.dependencies.no-assistants.installed"));

			} else {
				Notifications.info(project, MessageBundle.message("action.check.dependencies.no-assistants.title"),
						MessageBundle.message("action.check.dependencies.no-assistants.available", format(assistants)));
			}
			return;
		}

		String message;
		String supportedAssistants = format(assistants);
		if (!request.hasSelection()) {
			message = MessageBundle.message("action.check.dependencies.empty.noBuildFiles", supportedAssistants);
		} else if (reason == UpgradeScope.Reason.NOT_IMPORTED) {
			message = MessageBundle.message("action.check.dependencies.empty.notImported");
		} else {
			List<VirtualFile> selection = request.selection();
			message = selection.size() == 1
					? MessageBundle.message("action.check.dependencies.empty.unsupportedFile",
							selection.getFirst().getName(), supportedAssistants)
					: MessageBundle.message("action.check.dependencies.empty.unsupportedSelection", selection.size(),
							supportedAssistants);
		}

		Notifications.info(project, MessageBundle.message("action.check.dependencies.empty.title"), message);
	}

	private String format(Collection<DependencyAssistant> assistants) {
		return NlsMessages.formatOrList(assistants.stream().map(DependencyAssistant::getDisplayName).sorted().toList());
	}

	private String getTitle(UpgradeScope scope) {

		List<UpgradeScope.Entry> entries = scope.entries();
		if (entries.size() == 1) {
			UpgradeScope.Entry entry = entries.getFirst();
			return MessageBundle.message("dialog.title",
					entry.context().getInterfaceAssistant().getDisplayName(entry.buildFile().getVirtualFile()),
					project.getName());
		}

		return MessageBundle.message("dialog.title.multi", project.getName(), entries.size());
	}

}
