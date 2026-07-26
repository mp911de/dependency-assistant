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

package biz.paluch.dap.support;

import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Value object representing an artifact declaration and its associated
 * metadata.
 * <p>Captures the artifact identifier, the origin of the version, whether the
 * version is defined in the same file, and optional PSI elements for the
 * declaration and version.
 *
 * @author Mark Paluch
 */
public class ArtifactDeclaration implements DependencySite, HasPackageIdentity {

	private final PackageSystem packageSystem;

	private final ArtifactId artifactId;

	private final VersionSource versionSource;

	private final DeclarationSource declarationSource;

	private final boolean versionDefinedInSameFile;

	private final @Nullable ArtifactVersion version;

	private final PsiElement declarationElement;

	private final @Nullable PsiElement versionLiteral;

	private ArtifactDeclaration(PackageSystem packageSystem, ArtifactId artifactId, VersionSource versionSource,
			DeclarationSource declarationSource, boolean versionDefinedInSameFile,
			@Nullable ArtifactVersion version, PsiElement declarationElement,
			@Nullable PsiElement versionLiteral) {
		this.packageSystem = packageSystem;
		this.artifactId = artifactId;
		this.versionSource = versionSource;
		this.declarationSource = declarationSource;
		this.versionDefinedInSameFile = versionDefinedInSameFile;
		this.version = version;
		this.declarationElement = declarationElement;
		this.versionLiteral = versionLiteral;
	}

	/**
	 * Create a new {@link ArtifactDeclaration.Builder}.
	 * @return a new builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return a copy of this declaration with the given customizations applied.
	 * <p>The {@link Builder} is pre-populated with this declaration's state; the
	 * customizer overrides individual aspects, such as re-anchoring the
	 * {@linkplain Builder#declarationElement(PsiElement) declaration element} when
	 * a Gradle script consumes an artifact defined in a version catalog, or
	 * refining the {@linkplain Builder#declarationSource(DeclarationSource)
	 * declaration source} at the consuming call site. Whether the version is
	 * defined in the same file is re-derived from the resulting declaration element
	 * and version literal, see {@link Builder#build()}.
	 * @param customizer customizes the pre-populated builder; must not be
	 * {@literal null}.
	 * @return a new declaration carrying the customized state; never
	 * {@literal null}.
	 */
	public ArtifactDeclaration mutate(Consumer<Builder> customizer) {

		Builder builder = builder().artifact(artifactId)
				.packageSystem(packageSystem)
				.versionSource(versionSource)
				.declarationSource(declarationSource)
				.version(version)
				.declarationElement(declarationElement);
		if (versionLiteral != null) {
			builder.versionLiteral(versionLiteral);
		}

		customizer.accept(builder);
		return builder.build();
	}

	/**
	 * Return the artifact identifier.
	 *
	 * @return the artifact identifier.
	 */
	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	@Override
	public PackageSystem getPackageSystem() {
		return packageSystem;
	}

	/**
	 * Return whether this declaration has a concrete version source.
	 */
	public boolean hasVersionSource() {
		return getVersionSource().isDefined();
	}

	/**
	 * Return the source from which the version is obtained.
	 */
	public VersionSource getVersionSource() {
		return versionSource;
	}

	/**
	 * Return the source from which the declaration is obtained.
	 */
	public DeclarationSource getDeclarationSource() {
		return declarationSource;
	}

	/**
	 * Return whether the version is defined in the same file as the declaration.
	 *
	 * @return {@literal true} if the version is defined in the same file;
	 * {@literal false} otherwise.
	 */
	public boolean isVersionDefinedInSameFile() {
		return versionDefinedInSameFile;
	}

	/**
	 * Return whether a resolved version is available.
	 */
	public boolean isVersionDefined() {
		return version != null;
	}

	/**
	 * Return the resolved version.
	 * @throws IllegalStateException if no version is available.
	 */
	public ArtifactVersion getVersion() {
		Assert.state(version != null, "Version must not be null");
		return version;
	}

	/**
	 * Return the PSI element representing the declaration.
	 *
	 * @return the declaration element.
	 */
	public PsiElement getDeclarationElement() {
		return declarationElement;
	}

	/**
	 * Return the PSI element representing the version, if available.
	 *
	 * @return the version element, or {@literal null} if not available.
	 */
	public @Nullable PsiElement getVersionLiteral() {
		return versionLiteral;
	}

	/**
	 * Return the PSI element representing the version, failing if none is present.
	 *
	 * @return the version element.
	 * @throws IllegalStateException if no version literal is present.
	 */
	public PsiElement getRequiredVersionLiteral() {
		Assert.state(versionLiteral != null, "Version literal must not be null");
		return versionLiteral;
	}

	/**
	 * Adapt this declaration into a single-source {@link Dependency}.
	 *
	 * @return a dependency carrying this declaration's artifact, version, version
	 * source, and declaration source.
	 */
	public Dependency toDependency() {

		Dependency dependency = new Dependency(getPackageIdentity(), getVersion());
		dependency.addVersionSource(getVersionSource());
		dependency.addDeclarationSource(getDeclarationSource());

		return dependency;
	}

	@Override
	public String toString() {
		return artifactId + " {" +
				"versionSource=" + versionSource +
				", declarationSource=" + declarationSource +
				", version=" + version +
				'}';
	}

	/**
	 * Builder for {@link ArtifactDeclaration}.
	 */
	public static class Builder {

		private @Nullable ArtifactId id;

		private @Nullable PackageSystem packageSystem;

		private @Nullable VersionSource versionSource;

		private @Nullable DeclarationSource declarationSource;

		private @Nullable ArtifactVersion version;

		private @Nullable PsiElement declarationElement;

		private @Nullable PsiElement versionLiteral;

		private Builder() {
		}

		/**
		 * Configure the artifact identifier.
		 *
		 * @param id the artifact identifier.
		 * @return {@code this} builder.
		 */
		public Builder artifact(ArtifactId id) {
			this.id = id;
			return this;
		}

		/**
		 * Configure the package system.
		 *
		 * @param packageSystem the package system.
		 * @return {@code this} builder.
		 */
		public Builder packageSystem(PackageSystem packageSystem) {
			this.packageSystem = packageSystem;
			return this;
		}

		/**
		 * Configure the source from which the version was obtained.
		 *
		 * @param versionSource the version source.
		 * @return {@code this} builder.
		 */
		public Builder versionSource(VersionSource versionSource) {
			this.versionSource = versionSource;
			return this;
		}

		/**
		 * Configure the source from which the declaration was obtained.
		 *
		 * @param declarationSource the declaration source.
		 * @return {@code this} builder.
		 */
		public Builder declarationSource(DeclarationSource declarationSource) {
			this.declarationSource = declarationSource;
			return this;
		}

		/**
		 * Configure the artifact version.
		 *
		 * @param version the artifact version.
		 * @return {@code this} builder.
		 */
		public Builder version(@Nullable ArtifactVersion version) {
			this.version = version;
			return this;
		}

		/**
		 * Configure the PSI element representing the declaration.
		 *
		 * @param declarationElement the declaration element.
		 * @return {@code this} builder.
		 */
		public Builder declarationElement(PsiElement declarationElement) {
			this.declarationElement = declarationElement;
			return this;
		}

		/**
		 * Configure the PSI element representing the version literal, e.g. the value of
		 * a version property or literal.
		 *
		 * @param versionLiteral the version element.
		 * @return {@code this} builder.
		 */
		public Builder versionLiteral(PsiElement versionLiteral) {
			this.versionLiteral = versionLiteral;
			return this;
		}

		/**
		 * Build a new {@link ArtifactDeclaration}.
		 *
		 * @return a new {@link ArtifactDeclaration}.
		 * @throws IllegalArgumentException if the artifact id, version source,
		 * declaration source, or declaration element is not configured.
		 */
		public ArtifactDeclaration build() {

			Assert.notNull(packageSystem, "Package system must not be null");
			Assert.notNull(id, "ArtifactId must not be null");
			Assert.notNull(versionSource, "VersionSource must not be null");
			Assert.notNull(declarationSource, "DeclarationSource must not be null");
			Assert.notNull(declarationElement, "Declaration element must not be null");

			boolean versionDefinedInSameFile = versionLiteral != null && versionLiteral.getContainingFile()
					.equals(declarationElement.getContainingFile());

			return new ArtifactDeclaration(packageSystem, id, versionSource, declarationSource,
					versionDefinedInSameFile,
					version,
					declarationElement, versionLiteral);
		}

	}

}
