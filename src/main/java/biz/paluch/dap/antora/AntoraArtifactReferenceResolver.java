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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactReferenceResolver} implementation for Antora playbook
 * {@code ui.bundle.url} declarations.
 *
 * <p>Resolves the {@link YAMLScalar} value of a {@code ui.bundle.url} key into
 * an {@link ArtifactReference}. The declared version is resolved through the
 * canonical chain of
 * {@link GitVersionResolver#resolveCurrent(ArtifactId, String)}: the shared
 * release cache, then a raw {@link ArtifactVersion#from(String)} parse. Remote
 * API access is never triggered.
 *
 * @author Mark Paluch
 */
class AntoraArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final LookupContext context;

	private final AntoraProjectContext buildContext;

	/**
	 * Create a resolver for the given context and build context.
	 * @param context the shared per-file resolution environment.
	 * @param buildContext the Antora playbook context.
	 */
	AntoraArtifactReferenceResolver(LookupContext context, AntoraProjectContext buildContext) {
		this.context = context;
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
					.declarationSource(DeclarationSource.dependency())
					.versionSource(bundleUrl.toVersionSource())
					.declarationElement(scalar)
					.versionLiteral(scalar);

			context.versionResolver().resolveCurrent(artifactId, bundleUrl.version()).ifPresent(builder::version);
		});
	}

	/**
	 * Return the given element if it is the {@link YAMLScalar} value of a
	 * {@code ui.bundle.url} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if it is not the value of such a key.
	 */
	static @Nullable YAMLScalar findBundleUrlScalar(PsiElement element) {
		YamlVersionSite site = YamlVersionSite.locate(element, AntoraPlaybookParser::isBundleUrlKeyValue);
		return site != null ? site.scalar() : null;
	}

}
