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
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.VersionedPackage;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A transient dependency declaration with PSI anchors. The version literal may
 * belong to another file, such as a version catalog.
 *
 * <p>The resolved version and editable literal are independently optional.
 * Check {@link #isVersioned()} before accessing the version and
 * {@link #getVersionLiteral()} before requiring an editable anchor.
 *
 * @author Mark Paluch
 */
public class ArtifactDeclaration implements DependencySite, VersionedPackage {

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

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Copy this declaration with customizations, for example to re-anchor a catalog
	 * declaration at its usage. Same-file ownership is recomputed from the
	 * resulting anchors.
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

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	@Override
	public PackageSystem getPackageSystem() {
		return packageSystem;
	}

	public boolean hasVersionSource() {
		return getVersionSource().isDefined();
	}

	public VersionSource getVersionSource() {
		return versionSource;
	}

	public DeclarationSource getDeclarationSource() {
		return declarationSource;
	}

	/**
	 * Return whether an editable version literal belongs to the declaration's file.
	 */
	public boolean isVersionDefinedInSameFile() {
		return versionDefinedInSameFile;
	}

	@Override
	public boolean isVersioned() {
		return version != null;
	}

	/**
	 * Return the resolved version.
	 *
	 * @throws IllegalStateException if no version is available.
	 */
	@Override
	public ArtifactVersion getVersion() {
		Assert.state(version != null, "Version must not be null");
		return version;
	}

	public PsiElement getDeclarationElement() {
		return declarationElement;
	}

	/**
	 * Return the editable version anchor, or {@code null} if unavailable.
	 */
	public @Nullable PsiElement getVersionLiteral() {
		return versionLiteral;
	}

	/**
	 * Require an editable version anchor.
	 *
	 * @throws IllegalStateException if no version literal is present.
	 */
	public PsiElement getRequiredVersionLiteral() {
		Assert.state(versionLiteral != null, "Version literal must not be null");
		return versionLiteral;
	}

	/**
	 * Adapt this declaration into a dependency with its version and declaration
	 * sources.
	 *
	 * @throws IllegalStateException if no resolved version is available.
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

		public Builder artifact(ArtifactId id) {
			this.id = id;
			return this;
		}

		public Builder packageSystem(PackageSystem packageSystem) {
			this.packageSystem = packageSystem;
			return this;
		}

		public Builder versionSource(VersionSource versionSource) {
			this.versionSource = versionSource;
			return this;
		}

		public Builder declarationSource(DeclarationSource declarationSource) {
			this.declarationSource = declarationSource;
			return this;
		}

		public Builder version(@Nullable ArtifactVersion version) {
			this.version = version;
			return this;
		}

		public Builder declarationElement(PsiElement declarationElement) {
			this.declarationElement = declarationElement;
			return this;
		}

		public Builder versionLiteral(PsiElement versionLiteral) {
			this.versionLiteral = versionLiteral;
			return this;
		}

		/**
		 * Build a declaration. Version and version literal are optional.
		 *
		 * @throws IllegalArgumentException if identity, sources, or the declaration
		 * anchor are missing.
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
