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

	public static ArtifactReference unresolved() {
		return UNRESOLVED;
	}

	public static ArtifactReference from(Consumer<ArtifactDeclaration.Builder> builderConsumer) {
		ArtifactDeclaration.Builder builder = ArtifactDeclaration.builder();
		builderConsumer.accept(builder);
		return new ArtifactReference(builder.build());
	}

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

	public static ArtifactReference from(ArtifactDeclaration declaration) {
		return new ArtifactReference(declaration);
	}

	public boolean isResolved() {
		return declaration != null;
	}

	/**
	 * Return the declaration.
	 *
	 * @throws IllegalStateException if unresolved.
	 */
	public ArtifactDeclaration getDeclaration() {

		Assert.state(declaration != null, "No declaration available");
		return declaration;
	}

	/**
	 * Return the declaration's artifact id.
	 *
	 * @throws IllegalStateException if unresolved.
	 */
	public ArtifactId getArtifactId() {
		return getDeclaration().getArtifactId();
	}

	/**
	 * Adapt the declaration into a dependency with its version and declaration
	 * sources.
	 *
	 * @throws IllegalStateException if unresolved or the declaration has no
	 * version.
	 */
	public Dependency toDependency() {
		return getDeclaration().toDependency();
	}

	@Override
	public String toString() {
		return declaration == null ? "Unresolved" : "Resolved: " + declaration;
	}

}
