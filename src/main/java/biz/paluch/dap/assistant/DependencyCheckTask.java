/*
 * Copyright 2026 the original author or authors.
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

package biz.paluch.dap.assistant;

import java.util.List;

import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Background task that resolves the {@code Upgrade Scope}, runs a
 * {@code Dependency
 * Check} over it, and shows {@link DependencyCheckDialog} on success.
 *
 * @author Mark Paluch
 */
class DependencyCheckTask extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(DependencyCheckTask.class);

	private final Project project;

	private final UpgradeRequest request;

	private volatile @Nullable UpgradeScope scope;

	private volatile @Nullable DependencyUpgradeCandidates resultRef;

	public DependencyCheckTask(Project project, UpgradeRequest request) {
		super(project, MessageBundle.message("action.check.dependencies.progress"), true);
		this.project = project;
		this.request = request;
	}

	@Override
	public void run(ProgressIndicator indicator) {
		indicator.setIndeterminate(true);
		if (!request.hasSingleSource()) {
			indicator.setText(MessageBundle.message("action.check.dependencies.resolvingScope"));
		}

		UpgradeScope scope = ReadAction.nonBlocking(() -> UpgradeScopeResolver.resolve(project, request))
				.inSmartMode(project)
				.executeSynchronously();

		this.scope = scope;
		if (scope.isEmpty()) {
			indicator.stop();
			return;
		}

		indicator.setText(MessageBundle.message("action.check.dependencies.progress"));
		resultRef = new DependencyCheck(project).findDependencyUpgrades(indicator, scope,
				DependencyCheck.Consistency.CACHED);
	}

	@Override
	public void onSuccess() {

		UpgradeScope scope = this.scope;
		if (scope != null) {
			if (!scope.isEmpty()) {
				showResult(scope);
				return;
			}

			notifyNotFound(scope.reason());
		}
	}

	private void showResult(UpgradeScope scope) {

		DependencyUpgradeCandidates result = resultRef;
		if (result == null || result.isEmpty()) {
			Notifications.info(project, MessageBundle.message("plugin.name"),
					MessageBundle.message("action.check.dependencies.noUpdates"));
			return;
		}

		new DependencyCheckDialog(project, result, getTitle(scope)).show();
	}

	@Override
	public void onThrowable(Throwable error) {
		LOG.warn("Dependency check failed", error);

		Notifications.error(project,
				MessageBundle.message("action.check.dependencies.task.error", Notifications.errorMessage(error)));
	}

	private void notifyNotFound(UpgradeScope.Reason reason) {

		String message = switch (reason) {
		case NO_BUILD_FILES -> MessageBundle.message("action.check.dependencies.notFound.noBuildFiles");
		case NOT_IMPORTED -> MessageBundle.message("action.check.dependencies.notFound.notImported");
		case SUCCESS, DISCOVERY -> "";
		};

		Notifications.info(project, MessageBundle.message("plugin.name"), message);
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
