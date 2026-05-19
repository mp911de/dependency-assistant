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

package biz.paluch.dap.github;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * {@link VersionUpgradeLookupSupport} implementation for GitHub Actions
 * {@code uses:} declarations.
 *
 * <p>
 * Resolves {@code uses:} scalar values into an {@link ArtifactReference} by
 * parsing the scalar text and resolving the ref through
 * {@link GitVersionResolver#resolveCurrent(ArtifactId, String)}. The
 * canonical chain consults project state, the shared release cache, and a
 * raw {@link ArtifactVersion#from(String)} parse, in that order. Remote API
 * access is never triggered.
 *
 * <p>
 * Only elements inside the scalar value of a {@code uses:}
 * {@link YAMLKeyValue} are considered. All other elements resolve to
 * {@link ArtifactReference#unresolved()}.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private static final Predicate<YAMLKeyValue> IS_USES_KEY = kv -> "uses".equals(kv.getKeyText());

	private final GitHubProjectContext buildContext;

	/**
	 * Create a lookup service for the given project and build context.
	 * @param project the IntelliJ project.
	 * @param buildContext the GitHub Actions file context.
	 */
	VersionUpgradeLookupService(Project project, GitHubProjectContext buildContext) {
		super(project, buildContext);
		this.buildContext = buildContext;
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (element instanceof LeafPsiElement) {
			return ArtifactReference.unresolved();
		}

		YAMLScalar scalar = findUsesScalar(element);
		if (buildContext.isAbsent() || scalar == null) {
			return ArtifactReference.unresolved();
		}

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = ref.toArtifactId();
		return ArtifactReference.from(builder -> {
			builder.artifact(artifactId)
					.versionSource(ref.toVersionSource())
					.declarationElement(scalar)
					.versionLiteral(scalar);

			if (!StringUtils.hasText(ref.version())) {
				return;
			}

			getVersionResolver().resolveCurrent(artifactId, ref.version()).ifPresent(builder::version);
		});
	}

	/**
	 * Walk up the PSI tree from the given element to find the nearest
	 * {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if none found.
	 */
	public static @Nullable YAMLScalar findUsesScalar(PsiElement element) {

		YamlVersionSite site = YamlVersionSite.locate(element, IS_USES_KEY);
		return site != null ? site.scalar() : null;
	}

	/**
	 * Return the {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if it is not the value of such a key.
	 */
	public static @Nullable YAMLScalar getUsesScalar(PsiElement element) {

		if (!(element instanceof YAMLScalar scalar)) {
			return null;
		}
		YamlVersionSite site = YamlVersionSite.locate(scalar, IS_USES_KEY);
		return site != null ? site.scalar() : null;
	}

	public static @Nullable UsesRepositoryAction findUsesRepository(PsiElement element) {
		YAMLScalar scalar = findUsesScalar(element);
		if (scalar != null) {
			return GitHubWorkflowParser.parseUses(scalar.getTextValue());
		}
		return null;
	}

}
