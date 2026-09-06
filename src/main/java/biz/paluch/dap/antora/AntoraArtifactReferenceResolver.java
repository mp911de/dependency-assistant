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

package biz.paluch.dap.antora;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Resolve Antora {@code ui.bundle.url} references against cached Git releases.
 * <p>Unmatched refs remain available without remote lookup. Leaf elements and
 * unavailable playbook contexts produce unresolved references.
 *
 * @author Mark Paluch
 */
class AntoraArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final GitVersionResolver versionResolver;

	private final AntoraProjectContext buildContext;

	AntoraArtifactReferenceResolver(GitVersionResolver versionResolver, AntoraProjectContext buildContext) {
		this.versionResolver = versionResolver;
		this.buildContext = buildContext;
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (element instanceof LeafPsiElement) {
			return ArtifactReference.unresolved();
		}

		YAMLScalar scalar = findBundleUrlScalar(element);
		if (buildContext.isAbsent() || scalar == null) {
			return ArtifactReference.unresolved();
		}

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(scalar.getTextValue());
		if (bundleUrl == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = bundleUrl.toArtifactId();
		return ArtifactReference.from(builder -> {
			builder.artifact(artifactId)
					.packageSystem(PackageSystem.GITHUB)
					.declarationSource(DeclarationSource.dependency())
					.versionSource(bundleUrl.toVersionSource())
					.declarationElement(scalar)
					.versionLiteral(scalar);

			Versioned version = versionResolver.resolveLenient(artifactId, bundleUrl.version());
			if (version.isVersioned()) {
				builder.version(version.getVersion());
			}
		});
	}

	/**
	 * Find the containing {@code ui.bundle.url} scalar, or {@literal null} if
	 * absent.
	 */
	static @Nullable YAMLScalar findBundleUrlScalar(PsiElement element) {
		YamlVersionSite site = YamlVersionSite.locate(element, AntoraPlaybookParser::isBundleUrlKeyValue);
		return site != null ? site.scalar() : null;
	}

}
