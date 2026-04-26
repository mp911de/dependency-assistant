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
 * Identifies where and how the version of a dependency is defined in a build
 * file.
 * <p>A version source allows classification version origins, whether it is
 * provided as an inline literal or property. Additionally, version sources may
 * point to a {@link Profile} within the build file.
 * <p>The {@link #none()} singleton represents an absent version - used for
 * managed or catalog entries that carry no inline version string.
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
	 * <p>Returns {@literal false} only for the {@link #none()} singleton.
	 *
	 * @return {@literal true} if a version is defined; {@literal false} for the
	 * {@code none} sentinel.
	 */
	public boolean isDefined() {
		return this != NONE;
	}

	/**
	 * Return whether this version source is property-based.
	 * <p>Returns {@literal true} for any source that implements
	 * {@link VersionProperty}, covering both Maven property references and TOML
	 * {@code [versions]} entries.
	 *
	 * @return {@literal true} if this source implements {@link VersionProperty};
	 * {@literal false} otherwise.
	 */
	public boolean isProperty() {
		return this instanceof VersionProperty;
	}

	/**
	 * Return the sentinel representing an absent version.
	 * <p>Used for managed or catalog entries that do not carry an inline version
	 * string. {@link #isDefined()} returns {@literal false} for this instance.
	 *
	 * @return the {@code none} singleton; guaranteed to be not {@literal null}.
	 */
	public static VersionSource none() {
		return NONE;
	}

	/**
	 * Return a source representing a version held in a named build property (e.g. a
	 * Maven {@code ${property.name}} expression or a Gradle extra property).
	 *
	 * @param property the property name; must not be {@literal null}.
	 * @return a new instance implementing {@link VersionProperty}; guaranteed to be
	 * not {@literal null}.
	 */
	public static VersionSource property(String property) {
		return new VersionPropertySource(property);
	}

	/**
	 * Return a source representing a version string declared inline at the
	 * dependency site.
	 *
	 * @param version the literal version string; must not be {@literal null}.
	 * @return a new {@link DeclaredVersion} instance; guaranteed to be not
	 * {@literal null}.
	 */
	public static VersionSource declared(String version) {
		return new DeclaredVersion(version);
	}

	/**
	 * Return a source where the version is provided by the given declaration source
	 * (i.e. the version lives at a different structural location in the build file,
	 * not inline at this dependency site).
	 *
	 * @param declarationSource the declaration that carries the version; must not
	 * be {@literal null}.
	 * @return a new {@link VersionDeclarationSource} instance; guaranteed to be not
	 * {@literal null}.
	 */
	public static VersionSource declared(DeclarationSource declarationSource) {
		return new VersionDeclarationSource(declarationSource);
	}

	/**
	 * Return a source representing a version resolved from a TOML version catalog
	 * entry, where the catalog entry is the version itself (not a named
	 * {@code [versions]} property).
	 *
	 * @return the singleton instance; guaranteed to be not {@literal null}.
	 */
	public static VersionSource versionCatalog() {
		return CATALOG;
	}

	/**
	 * Return a source representing a version held in a named {@code [versions]}
	 * entry of a TOML version catalog.
	 *
	 * @param property the version property name in the catalog; must not be
	 * {@literal null}.
	 * @return a new {@link VersionCatalogProperty} instance implementing
	 * {@link VersionProperty}; guaranteed to be not {@literal null}.
	 */
	public static VersionSource versionCatalogProperty(String property) {
		return new VersionCatalogProperty(property);
	}

	/**
	 * Return a source representing a version held in a property declared within a
	 * named Maven profile.
	 *
	 * @param profile the profile identifier; must not be {@literal null}.
	 * @param property the property name within the profile; must not be
	 * {@literal null}.
	 * @return a new instance implementing both {@link VersionProperty} and
	 * {@link Profile}; guaranteed to be not {@literal null}.
	 */
	public static VersionSource profileProperty(String profile, String property) {
		return new ProfilePropertySource(property, profile);
	}

	/**
	 * Version source representing an absent version.
	 * <p>{@link #isDefined()} returns {@literal false} for this type. Obtained via
	 * {@link VersionSource#none()}.
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
	 * <p>The referenced declaration carries the version constraint; the updater
	 * navigates to that location via {@link #getDeclarationSource()} to apply the
	 * change. Obtained via {@link VersionSource#declared(DeclarationSource)}.
	 */
	public static class VersionDeclarationSource extends VersionSource {

		private final DeclarationSource declarationSource;

		public VersionDeclarationSource(DeclarationSource declarationSource) {
			this.declarationSource = declarationSource;
		}

		/**
		 * Return the declaration source that carries the version for this dependency.
		 *
		 * @return the declaration source; guaranteed to be not {@literal null}.
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
	 * <p>The updater writes the new version directly to the element that holds this
	 * string. Obtained via {@link VersionSource#declared(String)}.
	 */
	public static class DeclaredVersion extends VersionSource {

		private final String version;

		public DeclaredVersion(String version) {
			this.version = version;
		}

		/**
		 * Return the literal version string as declared in the build file.
		 *
		 * @return the version string; guaranteed to be not {@literal null}.
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
	 * TOML version catalog. Implements {@link VersionProperty};
	 * {@link #getProperty()} returns the catalog version key. Obtained via
	 * {@link VersionSource#versionCatalogProperty(String)}.
	 */
	public static class VersionCatalogProperty extends VersionSource implements VersionProperty {

		private final String property;

		public VersionCatalogProperty(String property) {
			this.property = property;
		}

		/**
		 * Return the version key in the TOML {@code [versions]} table.
		 *
		 * @return the property name; guaranteed to be not {@literal null}.
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

		public VersionPropertySource(String property) {
			this.property = property;
		}

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

		public ProfilePropertySource(String property, String profileId) {
			super(property);
			this.profileId = profileId;
		}

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
	 * <p>Implemented by sources backed by a Maven property reference and by TOML
	 * version catalog property entries. Callers can use a single
	 * {@code instanceof VersionProperty} check to handle both, then call
	 * {@link #getProperty()} to obtain the property name for lookup and update.
	 */
	public interface VersionProperty {

		/**
		 * Return the name of the property that holds the version.
		 *
		 * @return the property name; guaranteed to be not {@literal null}.
		 */
		String getProperty();

	}

	/**
	 * Marker interface for version sources scoped to a named Maven profile.
	 * <p>The updater uses the profile id to locate the property definition within
	 * the correct profile subtree when writing the new version back.
	 */
	public interface Profile {

		/**
		 * Return the identifier of the Maven profile that contains this version
		 * property.
		 *
		 * @return the profile id; guaranteed to be not {@literal null}.
		 */
		String getProfileId();

	}

}
