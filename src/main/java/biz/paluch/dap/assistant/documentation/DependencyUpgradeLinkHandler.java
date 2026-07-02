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

package biz.paluch.dap.assistant.documentation;

import java.util.List;

import biz.paluch.dap.assistant.action.DependencyCheckTask;
import biz.paluch.dap.assistant.action.UpgradeRequest;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Handles Dependency Assistant links in the Quick Documentation popup.
 *
 * <p>Version icons rendered by {@link DependencyDocumentationProvider} carry a
 * {@link #SCHEME}-prefixed link whose remainder is the target version. This
 * handler recognizes the scheme, applies the encoded version through the same
 * update path used by the upgrade quick-fix, and re-renders the popup against
 * the updated declaration.
 *
 * <p>The hidden-release notes carry a {@link #CHECK_SCHEME} link that opens the
 * Dependency Check dialog scoped to the documented declaration's build file,
 * with the artifact's row selected, so the full release history is one click
 * away from the digest.
 *
 * @author Mark Paluch
 * @see DependencyUpgradeTarget
 */
public class DependencyUpgradeLinkHandler implements DocumentationLinkHandler {

	/**
	 * URL scheme identifying an upgrade link. The remainder of the URL is the
	 * target version string.
	 */
	static final String SCHEME = "dependency-assistant-upgrade:";

	/**
	 * URL identifying an open-the-Dependency-Check-dialog link. The artifact and
	 * scope derive from the documentation target; the URL carries no payload.
	 */
	static final String CHECK_SCHEME = "dependency-assistant-check:";

	@Override
	public @Nullable LinkResolveResult resolveLink(DocumentationTarget target, String url) {

		if (!(target instanceof DependencyUpgradeTarget upgradeTarget)) {
			return null;
		}

		if (url.startsWith(CHECK_SCHEME)) {
			return openUpgradeDialog(target, upgradeTarget);
		}

		if (url.startsWith(SCHEME)) {
			return applyVersion(target, upgradeTarget, url.substring(SCHEME.length()));
		}

		return null;
	}

	private static LinkResolveResult applyVersion(DocumentationTarget target, DependencyUpgradeTarget upgradeTarget,
			String version) {

		Project project = upgradeTarget.getProject();

		return LinkResolveResult.asyncResult(() -> {

			if (project.isDisposed()) {
				return null;
			}

			ApplicationManager.getApplication().invokeAndWait(() -> WriteCommandAction.writeCommandAction(project)
					.withName(MessageBundle.message("documentation.upgrade-to", upgradeTarget.getArtifactId(), version))
					.run(() -> upgradeTarget.applyVersion(version)));

			return ReadAction.compute(() -> LinkResolveResult.Async.resolvedTarget(target));
		});
	}

	/**
	 * Launch a Dependency Check focused on the documented artifact; resolves back
	 * to the unchanged target so the popup stays intact.
	 */
	private static LinkResolveResult openUpgradeDialog(DocumentationTarget target,
			DependencyUpgradeTarget upgradeTarget) {

		Project project = upgradeTarget.getProject();

		return LinkResolveResult.asyncResult(() -> {

			if (project.isDisposed()) {
				return null;
			}

			PsiFile declarationFile = ReadAction.compute(upgradeTarget::getDeclarationFile);
			if (declarationFile != null) {
				ApplicationManager.getApplication()
						.invokeLater(() -> ProgressManager.getInstance().run(new DependencyCheckTask(project,
								new UpgradeRequest(List.of(), declarationFile, upgradeTarget.getArtifactId()))));
			}

			return ReadAction.compute(() -> LinkResolveResult.Async.resolvedTarget(target));
		});
	}

}
