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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiElement;

/**
 * A dependency site with a resolved version and PSI anchor. The declaration and
 * version anchors may point to different elements.
 *
 * @author Mark Paluch
 */
public interface VersionedDependencySite extends DependencySite {

	ArtifactVersion getVersion();

	/**
	 * Return the PSI anchor for the version literal or property usage.
	 */
	PsiElement getVersionElement();

	static VersionedDependencySite of(PackageIdentity pkg, ArtifactVersion version,
			VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement, PsiElement versionElement) {
		return new ResolvedDependencySite(pkg, version, versionSource, declarationSource,
				declarationElement, versionElement);
	}

}
