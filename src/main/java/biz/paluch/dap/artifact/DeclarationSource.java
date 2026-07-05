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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Structural location where a dependency or plugin is declared.
 *
 * <p>
 * Marker interfaces classify the artifact kind, whether the declaration is
 * managed, and whether it belongs to a Maven profile.
 *
 * @author Mark Paluch
 * @see Managed
 * @see Profile
 * @see VersionSource
 * @see DeclaredDependency
 */
public abstract class DeclarationSource {

	/**
	 * Return the source for a direct library dependency
	 * ({@code project/dependencies}).
	 */
	public static DeclarationSource dependency() {
		return Dependencies.INSTANCE;
	}

	/**
	 * Return the source for a managed library dependency
	 * ({@code project/dependencyManagement}).
	 */
	public static DeclarationSource managed() {
		return DependencyManagement.INSTANCE;
	}

	/**
	 * Return the source for a direct library dependency within the named Maven
	 * profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profileDependency(String id) {
		return new ProfileDependencies(id);
	}

	/**
	 * Return the source for a managed library dependency within the named Maven
	 * profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profileManaged(String id) {
		return new ProfileDependencyManagement(id);
	}

	/**
	 * Return the source for a Bill of Materials import
	 * ({@code project/dependencyManagement} with {@code scope=import} and
	 * {@code type=pom}, or a Gradle platform dependency).
	 */
	public static DeclarationSource bom() {
		return BomImport.INSTANCE;
	}

	/**
	 * Return the source for a Bill of Materials or a Gradle platform dependency.
	 */
	public static DeclarationSource bom(Map<ArtifactId, ArtifactVersion> artifacts) {
		return new BomImport(artifacts);
	}

	/**
	 * Return the source for a Bill of Materials import within the named Maven
	 * profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profileBom(String id) {
		return new ProfileBomImport(id, Map.of());
	}

	/**
	 * Return the source for a Bill of Materials import within the named Maven
	 * profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profileBom(String id, Map<ArtifactId, ArtifactVersion> artifacts) {
		return new ProfileBomImport(id, artifacts);
	}

	/**
	 * Return the source for a direct plugin ({@code project/build/plugins}).
	 */
	public static DeclarationSource plugin() {
		return Plugins.INSTANCE;
	}

	/**
	 * Return the source for a managed plugin
	 * ({@code project/build/pluginManagement}).
	 */
	public static DeclarationSource pluginManagement() {
		return PluginManagement.INSTANCE;
	}

	/**
	 * Return the source for a direct plugin within the named Maven profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profilePlugin(String id) {
		return new ProfilePlugins(id);
	}

	/**
	 * Return the source for a managed plugin within the named Maven profile.
	 * @param id the profile identifier.
	 */
	public static DeclarationSource profilePluginManagement(String id) {
		return new ProfilePluginManagement(id);
	}

	/**
	 * Check if the current declaration source is a plugin.
	 * @return {@literal true} if the source is a plugin; {@literal false}
	 * otherwise.
	 */
	public boolean isPlugin() {
		return this instanceof DeclarationSource.Plugin;
	}

	/**
	 * Return {@literal true} if all the given sources are plugins. This is useful
	 * to determine whether an artifact is solely used as plugin or addtionally as
	 * dependency.
	 * @param declarationSources the declaration sources to check.
	 * @return {@literal true} if the collection is non-empty and every source is a
	 * plugin; {@literal false} otherwise, including for an empty collection.
	 */
	public static boolean isPlugin(Collection<DeclarationSource> declarationSources) {
		int plugin = 0;
		for (DeclarationSource source : declarationSources) {
			if (source.isPlugin()) {
				plugin++;
			} else {
				return false;
			}
		}
		return plugin > 0 && declarationSources.size() == plugin;
	}

	@Override
	public abstract String toString();

	/**
	 * Marker interface for a build plugin declaration (e.g. Maven
	 * {@code <build><plugins>} or Gradle {@code plugins {}}).
	 */
	public interface Plugin {

	}

	/**
	 * Marker interface for a library dependency declaration (e.g. Maven
	 * {@code <dependencies>} or a Gradle dependency configuration).
	 */
	public interface Dependency {

	}

	/**
	 * Marker interface for a version-constraint entry in a management section
	 * rather than an active dependency or plugin use.
	 */
	public interface Managed {

	}

	/**
	 * Marker interface for a Bill of Materials import declaration (e.g. Maven
	 * {@code dependencyManagement} entries with {@code scope=import} and
	 * {@code type=pom}, or Gradle {@code platform(...)} dependencies).
	 *
	 * <p>A BOM source is definitionally a managed dependency source, so every
	 * {@code instanceof Managed} check keeps matching.
	 */
	public interface Bom extends Managed {

		Map<ArtifactId, ArtifactVersion> getArtifacts();

	}

	/**
	 * Marker interface for a declaration scoped to a named Maven profile.
	 */
	public interface Profile {

		/**
		 * Return the identifier of the Maven profile that contains this declaration.
		 */
		String getProfileId();

	}

	/**
	 * Dependencies under project/dependencies.
	 */
	private static class Dependencies extends DeclarationSource implements Dependency {

		/**
		 * Shared dependencies source.
		 */
		public static final Dependencies INSTANCE = new Dependencies();

		private Dependencies() {
		}

		@Override
		public String toString() {
			return "DP";
		}

	}

	/**
	 * Dependencies under project/dependencyManagement.
	 */
	private static class DependencyManagement extends DeclarationSource implements Dependency, Managed {

		/**
		 * Shared dependency management source.
		 */
		public static final DependencyManagement INSTANCE = new DependencyManagement();

		private DependencyManagement() {
		}

		@Override
		public String toString() {
			return "DM";
		}

	}

	/**
	 * Dependencies under a profile's dependencies.
	 */
	private static class ProfileDependencies extends DeclarationSource implements Dependency, Profile {

		private final String profileId;

		private ProfileDependencies(String profileId) {
			this.profileId = profileId;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			ProfileDependencies that = (ProfileDependencies) o;
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(profileId);
		}

		@Override
		public String toString() {
			return "profile:" + profileId;
		}

	}

	/**
	 * Dependencies under a profile's dependencyManagement.
	 */
	private static class ProfileDependencyManagement extends DeclarationSource implements Dependency, Profile, Managed {

		private final String profileId;

		private ProfileDependencyManagement(String profileId) {
			this.profileId = profileId;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			ProfileDependencyManagement that = (ProfileDependencyManagement) o;
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(profileId);
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/DM";
		}

	}

	/**
	 * Bill of Materials imports under project/dependencyManagement.
	 */
	private static class BomImport extends DeclarationSource implements Dependency, Bom {

		/**
		 * Shared BOM import source.
		 */
		public static final BomImport INSTANCE = new BomImport(Map.of());

		private final Map<ArtifactId, ArtifactVersion> artifacts;

		BomImport(Map<ArtifactId, ArtifactVersion> artifacts) {
			this.artifacts = artifacts;
		}

		@Override
		public Map<ArtifactId, ArtifactVersion> getArtifacts() {
			return artifacts;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			// identity is the source kind; the member map is metadata, as with
			// ProfileBomImport
			return o != null && getClass() == o.getClass();
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public String toString() {
			return "BOM";
		}

	}

	/**
	 * Bill of Materials imports under a profile's dependencyManagement.
	 */
	private static class ProfileBomImport extends DeclarationSource implements Dependency, Profile, Bom {

		private final String profileId;

		private final Map<ArtifactId, ArtifactVersion> artifacts;

		ProfileBomImport(String profileId, Map<ArtifactId, ArtifactVersion> artifacts) {
			this.profileId = profileId;
			this.artifacts = artifacts;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public Map<ArtifactId, ArtifactVersion> getArtifacts() {
			return artifacts;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			ProfileBomImport that = (ProfileBomImport) o;
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(profileId);
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/BOM";
		}

	}

	/**
	 * Plugins under project/build/plugins.
	 */
	private static class Plugins extends DeclarationSource implements Plugin {

		/**
		 * Shared plugins source.
		 */
		public static final Plugins INSTANCE = new Plugins();

		private Plugins() {
		}

		@Override
		public String toString() {
			return "PL";
		}

	}

	/**
	 * Plugins under project/build/pluginManagement.
	 */
	private static class PluginManagement extends DeclarationSource implements Plugin, Managed {

		/**
		 * Shared plugin management source.
		 */
		public static final PluginManagement INSTANCE = new PluginManagement();

		private PluginManagement() {
		}

		@Override
		public String toString() {
			return "PM";
		}

	}

	/**
	 * Plugins under a profile's build/plugins.
	 */
	private static class ProfilePlugins extends DeclarationSource implements Plugin, Profile {

		private final String profileId;

		private ProfilePlugins(String profileId) {
			this.profileId = profileId;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			ProfilePlugins that = (ProfilePlugins) o;
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(profileId);
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/PL";
		}

	}


	/**
	 * Plugins under a profile's build/pluginManagement.
	 */
	private static class ProfilePluginManagement extends DeclarationSource implements Plugin, Profile, Managed {

		private final String profileId;

		private ProfilePluginManagement(String profileId) {
			this.profileId = profileId;
		}

		/**
		 * Return the Maven profile id.
		 */
		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			ProfilePluginManagement that = (ProfilePluginManagement) o;
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(profileId);
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/PM";
		}

	}

}
