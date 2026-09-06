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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Resolves GitHub Actions references using cached Git metadata.
 * <p>Accepts structural elements inside a {@code uses:} scalar. Unsupported
 * elements and unavailable contexts produce an unresolved reference. No network
 * access is required.
 *
 * @author Mark Paluch
 */
class GitHubArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final GitVersionResolver versionResolver;

	private final GitHubProjectContext buildContext;

	GitHubArtifactReferenceResolver(GitVersionResolver versionResolver, GitHubProjectContext buildContext) {
		this.versionResolver = versionResolver;
		this.buildContext = buildContext;
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (element.getFirstChild() == null) {
			return ArtifactReference.unresolved();
		}

		YAMLScalar scalar = GitHubUtils.findUsesScalar(element);
		if (buildContext.isAbsent() || scalar == null) {
			return ArtifactReference.unresolved();
		}

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = ref.getArtifactId();
		return ArtifactReference.from(builder -> {
			builder.artifact(artifactId)
					.packageSystem(PackageSystem.GITHUB)
					.versionSource(ref.toVersionSource())
					.declarationSource(DeclarationSource.dependency())
					.declarationElement(scalar)
					.versionLiteral(scalar);

			if (!StringUtils.hasText(ref.version())) {
				return;
			}

			Versioned version = versionResolver.resolveLenient(artifactId, ref.version());
			if (version.isVersioned()) {
				builder.version(version.getVersion());
			}
		});
	}

	public static @Nullable UsesRepositoryAction findUsesRepository(PsiElement element) {
		YAMLScalar scalar = GitHubUtils.findUsesScalar(element);
		if (scalar != null) {
			return GitHubWorkflowParser.parseUses(scalar.getTextValue());
		}
		return null;
	}

}
