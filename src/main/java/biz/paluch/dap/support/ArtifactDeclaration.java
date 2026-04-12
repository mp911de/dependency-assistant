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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
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
public class ArtifactDeclaration {

	private final ArtifactId artifactId;

	private final VersionSource versionSource;

	private final boolean versionDefinedInSameFile;

	private final @Nullable ArtifactVersion version;

	private final @Nullable PsiElement declarationElement;

	private final @Nullable PsiElement versionLiteral;

	private ArtifactDeclaration(Builder builder) {
		this.artifactId = builder.id;
		this.versionSource = builder.versionSource;
		this.versionDefinedInSameFile = builder.versionDefinedInSameFile;
		this.version = builder.version;
		this.declarationElement = builder.declarationElement;
		this.versionLiteral = builder.versionLiteral;
	}

	/**
	 * Return the artifact identifier.
	 *
	 * @return the artifact identifier.
	 */
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public boolean hasVersionSource() {
		return getVersionSource().isDefined();
	}

	public VersionSource getVersionSource() {
		return versionSource;
	}

	/**
	 * Return whether the version is defined in the same file as the declaration.
	 *
	 * @return {@code true} if the version is defined in the same file;
	 * {@code false} otherwise.
	 */
	public boolean isVersionDefinedInSameFile() {
		return versionDefinedInSameFile;
	}

	public boolean isVersionDefined() {
		return version != null;
	}

	public ArtifactVersion getVersion() {
		Assert.state(version != null, "Version must not be null");
		return version;
	}

	/**
	 * Return the PSI element representing the declaration, if available.
	 *
	 * @return the declaration element, or {@code null} if not available.
	 */
	public @Nullable PsiElement getDeclarationElement() {
		return declarationElement;
	}

	/**
	 * Return the PSI element representing the version, if available.
	 *
	 * @return the version element, or {@code null} if not available.
	 */
	public @Nullable PsiElement getVersionLiteral() {
		return versionLiteral;
	}

	/**
	 * Create a new {@link ArtifactDeclaration.Builder}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ArtifactDeclaration}.
	 */
	public static class Builder {

		private @Nullable ArtifactId id;

		private @Nullable VersionSource versionSource;

		private boolean versionDefinedInSameFile;

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
		 * Indicate that the version is defined in a different file.
		 *
		 * @return {@code this} builder.
		 */
		public Builder versionDefinedInOtherFile() {
			this.versionDefinedInSameFile = false;
			return this;
		}

		/**
		 * Indicate that the version is defined in the same file.
		 *
		 * @return {@code this} builder.
		 */
		public Builder versionDefinedInSameFile() {
			this.versionDefinedInSameFile = true;
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

			if (versionLiteral != null) {
				this.versionDefinedInSameFile = versionLiteral.getContainingFile()
						.equals(declarationElement.getContainingFile());
			}
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

			if (declarationElement != null) {
				this.versionDefinedInSameFile = versionLiteral.getContainingFile()
						.equals(declarationElement.getContainingFile());
			}
			return this;
		}

		/**
		 * Build a new {@link ArtifactDeclaration}.
		 * <p>Requires both {@link ArtifactId} and {@link VersionSource} to be
		 * configured.
		 *
		 * @return a new {@link ArtifactDeclaration}.
		 * @throws IllegalArgumentException if {@code id} or {@code versionSource} is
		 * {@code null}.
		 */
		public ArtifactDeclaration build() {

			Assert.notNull(id, "ArtifactId must not be null");
			Assert.notNull(versionSource, "VersionSource must not be null");

			return new ArtifactDeclaration(this);
		}

	}

}
