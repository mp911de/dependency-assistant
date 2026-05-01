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

import java.util.List;
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * {@link VersionUpgradeLookupSupport} implementation for GitHub Actions
 * workflow files.
 *
 * <p>Resolves {@code uses:} scalar values into an {@link ArtifactReference} by
 * parsing the scalar text and resolving the ref against the shared cache via
 * {@link GitVersionResolver}. Remote API access is never triggered.
 *
 * <p>Only elements that are children of a {@code uses:} {@link YAMLKeyValue}
 * are considered. All other elements resolve to
 * {@link ArtifactReference#unresolved()}.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final GitHubProjectContext buildContext;

	/**
	 * Create a lookup service for the given project and build context.
	 * @param project the IntelliJ project.
	 * @param buildContext the workflow file context.
	 */
	VersionUpgradeLookupService(Project project, GitHubProjectContext buildContext) {
		super(project, buildContext);
		this.buildContext = buildContext;
	}

	@Override
	protected AvailableUpgrades suggestUpgrades(Cache cache, ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return AvailableUpgrades.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return AvailableUpgrades.none();
		}

		List<Release> releases = cache.getReleases(declaration.getArtifactId());

		if (releases.isEmpty()) {
			return AvailableUpgrades.none();
		}

		return determineUpgrades(artifactReference, declaration.getVersion(), releases);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

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

			Dependency dependency = getProjectState().findDependency(artifactId);
			if (dependency != null) {
				builder.version(dependency.getCurrentVersion());
			} else if (StringUtils.hasText(ref.version())) {
				GitVersionResolver resolver = new GitVersionResolver(getCache());
				Optional<GitVersion> version = resolver.resolve(artifactId, ref.version());
				if (version.isEmpty()) {
					ArtifactVersion.from(ref.version()).ifPresent(builder::version);
				} else {
					version.ifPresent(builder::version);
				}
			}
		});
	}

	/**
	 * Walk up the PSI tree from the given element to find the nearest
	 * {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@code null} if none found.
	 */
	static @Nullable YAMLScalar findUsesScalar(PsiElement element) {

		if (element instanceof YAMLScalar scalar) {
			return getUsesScalar(scalar);
		}

		YAMLScalar scalar = PsiTreeUtil.getParentOfType(element, YAMLScalar.class, false, YAMLMapping.class);
		return scalar != null ? getUsesScalar(scalar) : null;
	}

	/**
	 * Return the {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@code null} if none found.
	 */
	static @Nullable YAMLScalar getUsesScalar(PsiElement element) {

		YAMLScalar scalar = element instanceof YAMLScalar s ? s
				: null;
		if (scalar != null && scalar.getParent() instanceof YAMLKeyValue keyValue) {
			if ("uses".equals(keyValue.getKeyText())) {
				return scalar;
			}
		}
		return null;
	}

}
