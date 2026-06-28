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

import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import org.jspecify.annotations.Nullable;

/**
 * Applies a dependency upgrade when a version link in the Quick Documentation
 * popup is clicked.
 *
 * <p>Version icons rendered by {@link DependencyDocumentationProvider} carry a
 * {@link #SCHEME}-prefixed link whose remainder is the target version. This
 * handler recognizes the scheme, applies the encoded version through the same
 * update path used by the upgrade quick-fix, and re-renders the popup against
 * the updated declaration.
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

	@Override
	public @Nullable LinkResolveResult resolveLink(DocumentationTarget target, String url) {

		if (!url.startsWith(SCHEME) || !(target instanceof DependencyUpgradeTarget upgradeTarget)) {
			return null;
		}

		String version = url.substring(SCHEME.length());
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

}
