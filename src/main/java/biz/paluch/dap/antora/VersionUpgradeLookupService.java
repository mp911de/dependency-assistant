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

package biz.paluch.dap.antora;

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * {@link VersionUpgradeLookupSupport} implementation for Antora playbook
 * {@code ui.bundle.url} declarations.
 *
 * <p>Resolves the {@link YAMLScalar} value of a {@code ui.bundle.url} key into
 * an {@link ArtifactReference}. The declared version is resolved against the
 * shared cache through {@link GitVersionResolver}; if the cache does not
 * contain the version, the parsed version is exposed as a plain
 * {@link ArtifactVersion}. Remote API access is never triggered.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final AntoraProjectContext buildContext;

	/**
	 * Create a lookup service for the given project and build context.
	 * @param project the IntelliJ project.
	 * @param buildContext the Antora playbook context.
	 */
	VersionUpgradeLookupService(Project project, AntoraProjectContext buildContext) {
		super(project, buildContext);
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
					.versionSource(bundleUrl.toVersionSource())
					.declarationElement(scalar)
					.versionLiteral(scalar);

			Dependency dependency = getProjectState().findDependency(artifactId);
			if (dependency != null) {
				builder.version(dependency.getCurrentVersion());
				return;
			}

			GitVersionResolver resolver = new GitVersionResolver(getCache());
			Optional<GitVersion> resolved = resolver.resolve(artifactId, bundleUrl.version());
			if (resolved.isPresent()) {
				resolved.ifPresent(builder::version);
				return;
			}
			ArtifactVersion.from(bundleUrl.version()).ifPresent(builder::version);
		});
	}

	/**
	 * Walk up the PSI tree from the given element to find the nearest
	 * {@link YAMLScalar} that is the value of a {@code ui.bundle.url} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if none found.
	 */
	static @Nullable YAMLScalar findBundleUrlScalar(PsiElement element) {

		if (element instanceof YAMLScalar scalar) {
			return getBundleUrlScalar(scalar);
		}

		YAMLScalar scalar = PsiTreeUtil.getParentOfType(element, YAMLScalar.class, false, YAMLMapping.class);
		return scalar != null ? getBundleUrlScalar(scalar) : null;
	}

	/**
	 * Return the given element if it is the {@link YAMLScalar} value of a
	 * {@code ui.bundle.url} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if it is not the value of such a key.
	 */
	static @Nullable YAMLScalar getBundleUrlScalar(PsiElement element) {

		if (!(element instanceof YAMLScalar scalar)) {
			return null;
		}
		if (!(scalar.getParent() instanceof YAMLKeyValue keyValue)) {
			return null;
		}
		return AntoraPlaybookParser.isBundleUrlKeyValue(keyValue) ? scalar : null;
	}

	/**
	 * Return the parsed bundle URL for the {@code ui.bundle.url} scalar that
	 * encloses the given element, or {@literal null} when no such scalar exists or
	 * the value cannot be parsed.
	 * @param element the element at the cursor position.
	 * @return the parsed bundle URL, or {@literal null}.
	 */
	static @Nullable AntoraBundleUrl findBundleUrl(PsiElement element) {

		YAMLScalar scalar = findBundleUrlScalar(element);
		return scalar != null ? AntoraBundleUrl.from(scalar.getTextValue()) : null;
	}

}
