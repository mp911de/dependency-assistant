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

import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Result of resolving a build-file element to an {@link ArtifactDeclaration}.
 *
 * <p>An unresolved reference contains no declaration. A resolved reference may
 * still contain an unversioned declaration, so {@link #isResolved()} does not
 * imply that {@link ArtifactDeclaration#getVersion()} is available.
 *
 * @author Mark Paluch
 */
public class ArtifactReference {

	private static final ArtifactReference UNRESOLVED = new ArtifactReference(null);

	private final @Nullable ArtifactDeclaration declaration;

	private ArtifactReference(@Nullable ArtifactDeclaration declaration) {
		this.declaration = declaration;
	}

	/**
	 * Return the shared unresolved lookup result.
	 *
	 * @return an artifact reference containing no declaration.
	 */
	public static ArtifactReference unresolved() {
		return UNRESOLVED;
	}

	/**
	 * Create a resolved reference by configuring an
	 * {@link ArtifactDeclaration.Builder}.
	 *
	 * @param builderConsumer the declaration builder customizations.
	 * @return a reference containing the built declaration.
	 */
	public static ArtifactReference from(Consumer<ArtifactDeclaration.Builder> builderConsumer) {
		ArtifactDeclaration.Builder builder = ArtifactDeclaration.builder();
		builderConsumer.accept(builder);
		return new ArtifactReference(builder.build());
	}

	/**
	 * Create an {@code ArtifactReference} from the given
	 * {@link VersionedDependencySite}.
	 *
	 * @param dependencySite the versioned dependency site to adapt.
	 * @return a reference containing the adapted declaration.
	 */
	public static ArtifactReference from(VersionedDependencySite dependencySite) {
		return from(it -> {
			it.artifact(dependencySite.getArtifactId())
					.packageSystem(dependencySite.getPackageSystem())
					.version(dependencySite.getVersion())
					.versionSource(dependencySite.getVersionSource())
					.declarationSource(dependencySite.getDeclarationSource())
					.declarationElement(dependencySite.getDeclarationElement())
					.versionLiteral(dependencySite.getVersionElement());
		});
	}

	/**
	 * Create an artifact reference from the given artifact declaration.
	 * @param declaration the resolved artifact declaration.
	 * @return the artifact reference.
	 */
	public static ArtifactReference from(ArtifactDeclaration declaration) {
		return new ArtifactReference(declaration);
	}

	/**
	 * Return whether this reference contains an artifact declaration.
	 *
	 * @return {@literal true} if this reference contains a declaration;
	 * {@literal false} otherwise.
	 */
	public boolean isResolved() {
		return declaration != null;
	}

	/**
	 * Return the resolved {@link ArtifactDeclaration}.
	 *
	 * @return the resolved declaration.
	 * @throws IllegalStateException if this reference is unresolved.
	 */
	public ArtifactDeclaration getDeclaration() {

		Assert.state(declaration != null, "No declaration available");
		return declaration;
	}

	/**
	 * Return the artifact id of the resolved declaration.
	 *
	 * @return the resolved declaration's artifact id.
	 * @throws IllegalStateException if this reference is unresolved.
	 */
	public ArtifactId getArtifactId() {
		return getDeclaration().getArtifactId();
	}

	/**
	 * Adapt this reference's resolved declaration into a single-source
	 * {@link Dependency}.
	 *
	 * @return the dependency derived from the resolved declaration.
	 * @throws IllegalStateException if this reference is unresolved or its
	 * declaration has no resolved version.
	 */
	public Dependency toDependency() {
		return getDeclaration().toDependency();
	}

	@Override
	public String toString() {
		return declaration == null ? "Unresolved" : "Resolved: " + declaration;
	}

}
