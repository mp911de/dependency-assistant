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

package biz.paluch.dap.artifact;

import java.util.Objects;

/**
 * Source from which a dependency version is obtained in a build file.
 *
 * <p>A version may be declared inline, resolved from a property, provided by a
 * managed declaration, or absent.
 *
 * @author Mark Paluch
 * @see VersionProperty
 * @see Profile
 * @see DeclarationSource
 * @see DeclaredDependency
 */
public abstract class VersionSource {

	private static final NoVersionSource NONE = new NoVersionSource();

	private static final VersionCatalog CATALOG = new VersionCatalog();

	/**
	 * Return whether a version is defined.
	 * <p>Returns {@code false} only for the {@link #none()} singleton.
	 */
	public boolean isDefined() {
		return this != NONE;
	}

	/**
	 * Return whether this version source is property-based.
	 */
	public boolean isProperty() {
		return this instanceof VersionProperty;
	}

	/**
	 * Return the sentinel representing an absent version.
	 */
	public static VersionSource none() {
		return NONE;
	}

	/**
	 * Return a source representing a version held in a named build property.
	 * @param property the property name.
	 */
	public static VersionSource property(String property) {
		return new VersionPropertySource(property);
	}

	/**
	 * Return a source representing a version string declared inline at the
	 * dependency site.
	 * @param version the literal version string.
	 */
	public static VersionSource declared(String version) {
		return new DeclaredVersion(version);
	}

	/**
	 * Return a source where the version is provided by the given declaration source
	 * rather than inline at the dependency site.
	 * @param declarationSource the declaration that carries the version.
	 */
	public static VersionSource declared(DeclarationSource declarationSource) {
		return new VersionDeclarationSource(declarationSource);
	}

	/**
	 * Return a source representing a version resolved from a TOML version catalog
	 * entry.
	 */
	public static VersionSource versionCatalog() {
		return CATALOG;
	}

	/**
	 * Return a source representing a version held in a named {@code [versions]}
	 * entry of a TOML version catalog.
	 * @param property the version property name in the catalog.
	 */
	public static VersionSource versionCatalogProperty(String property) {
		return new VersionCatalogProperty(property);
	}

	/**
	 * Return a source representing a version held in a property declared within a
	 * named Maven profile.
	 * @param profile the profile identifier.
	 * @param property the property name within the profile.
	 */
	public static VersionSource profileProperty(String profile, String property) {
		return new ProfilePropertySource(property, profile);
	}

	/**
	 * Version source representing an absent version.
	 * <p>{@link #isDefined()} returns {@code false} for this type.
	 */
	public static class NoVersionSource extends VersionSource {

		@Override
		public String toString() {
			return "none";
		}

	}

	/**
	 * Version source where the version is provided by a separate
	 * {@link DeclarationSource} rather than being declared inline.
	 */
	public static class VersionDeclarationSource extends VersionSource {

		private final DeclarationSource declarationSource;

		/**
		 * Create a new {@code VersionDeclarationSource}.
		 * @param declarationSource the declaration source that carries the version.
		 */
		public VersionDeclarationSource(DeclarationSource declarationSource) {
			this.declarationSource = declarationSource;
		}

		/**
		 * Return the declaration source that carries the version for this dependency.
		 */
		public DeclarationSource getDeclarationSource() {
			return declarationSource;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionDeclarationSource that)) {
				return false;
			}
			return Objects.equals(declarationSource, that.declarationSource);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(declarationSource);
		}

	}

	/**
	 * Version source for a version string declared inline at the dependency site.
	 */
	public static class DeclaredVersion extends VersionSource {

		private final String version;

		/**
		 * Create a new {@code DeclaredVersion}.
		 * @param version the literal version string.
		 */
		public DeclaredVersion(String version) {
			this.version = version;
		}

		/**
		 * Return the literal version string as declared in the build file.
		 */
		public String getVersion() {
			return version;
		}

		@Override
		public String toString() {
			return version;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof DeclaredVersion that)) {
				return false;
			}
			return Objects.equals(version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(version);
		}

	}

	/**
	 * Version source for a version resolved from a TOML version catalog entry.
	 */
	public static class VersionCatalog extends VersionSource {

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionCatalog that)) {
				return false;
			}
			return Objects.equals(getClass(), that.getClass());
		}

		@Override
		public int hashCode() {
			return 111;
		}

	}

	/**
	 * Version source for a version held in a named {@code [versions]} entry of a
	 * TOML version catalog.
	 */
	public static class VersionCatalogProperty extends VersionSource implements VersionProperty {

		private final String property;

		/**
		 * Create a new {@code VersionCatalogProperty}.
		 * @param property the version property name.
		 */
		public VersionCatalogProperty(String property) {
			this.property = property;
		}

		/**
		 * Return the version key in the TOML {@code [versions]} table.
		 */
		public String getProperty() {
			return property;
		}

		@Override
		public String toString() {
			return "${" + property + '}';
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionCatalogProperty that)) {
				return false;
			}
			return Objects.equals(getClass(), that.getClass()) && Objects.equals(property, that.property);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(property);
		}

	}

	private static class VersionPropertySource extends VersionSource implements VersionProperty {

		private final String property;

		/**
		 * Create a new {@code VersionPropertySource}.
		 * @param property the property name.
		 */
		public VersionPropertySource(String property) {
			this.property = property;
		}

		/**
		 * Return the name of the property that holds the version.
		 */
		public String getProperty() {
			return property;
		}

		@Override
		public String toString() {
			return "${" + property + '}';
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionPropertySource that)) {
				return false;
			}
			return Objects.equals(getClass(), that.getClass()) && Objects.equals(property, that.property);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(property);
		}

	}

	private static class ProfilePropertySource extends VersionPropertySource implements Profile {

		private final String profileId;

		/**
		 * Create a new {@code ProfilePropertySource}.
		 * @param property the property name.
		 * @param profileId the Maven profile id.
		 */
		public ProfilePropertySource(String property, String profileId) {
			super(property);
			this.profileId = profileId;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof ProfilePropertySource that)) {
				return false;
			}
			if (!super.equals(o)) {
				return false;
			}
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 31 + Objects.hashCode(profileId);
		}

	}

	/**
	 * Marker interface for version sources that hold the version in a named
	 * property rather than as an inline string.
	 */
	public interface VersionProperty {

		/**
		 * Return the name of the property that holds the version.
		 */
		String getProperty();

	}

	/**
	 * Marker interface for version sources scoped to a named Maven profile.
	 */
	public interface Profile {

		/**
		 * Return the identifier of the Maven profile that contains this version
		 * property.
		 */
		String getProfileId();

	}

}
