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

package biz.paluch.dap.artifact;

import java.util.Collection;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Structural location where a dependency or plugin is declared.
 *
 * <p>Marker interfaces classify the artifact kind, whether the declaration is
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
	 */
	public static DeclarationSource profileDependency(String id) {
		return new ProfileDependencies(id);
	}

	/**
	 * Return the source for a managed library dependency within the named Maven
	 * profile.
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
	 * Return the source for a Bill of Materials import within the named Maven
	 * profile.
	 */
	public static DeclarationSource profileBom(String id) {
		return new ProfileBomImport(id);
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
	 */
	public static DeclarationSource profilePlugin(String id) {
		return new ProfilePlugins(id);
	}

	/**
	 * Return the source for a managed plugin within the named Maven profile.
	 */
	public static DeclarationSource profilePluginManagement(String id) {
		return new ProfilePluginManagement(id);
	}

	public boolean isPlugin() {
		return this instanceof DeclarationSource.Plugin;
	}

	/**
	 * Return whether every source is a plugin declaration. An empty collection is
	 * not plugin-only.
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
	 * A Bill of Materials import.
	 * <p>Membership is resolved separately as a {@link BillOfMaterials}.
	 */
	public interface Bom extends Managed {

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

	private static class Dependencies extends DeclarationSource implements Dependency {

		public static final Dependencies INSTANCE = new Dependencies();

		private Dependencies() {
		}

		@Override
		public String toString() {
			return "DP";
		}

	}

	private static class DependencyManagement extends DeclarationSource implements Dependency, Managed {

		public static final DependencyManagement INSTANCE = new DependencyManagement();

		private DependencyManagement() {
		}

		@Override
		public String toString() {
			return "DM";
		}

	}

	private static class ProfileDependencies extends DeclarationSource implements Dependency, Profile {

		private final String profileId;

		private ProfileDependencies(String profileId) {
			this.profileId = profileId;
		}

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

	private static class ProfileDependencyManagement extends DeclarationSource implements Dependency, Profile, Managed {

		private final String profileId;

		private ProfileDependencyManagement(String profileId) {
			this.profileId = profileId;
		}

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

	private static class BomImport extends DeclarationSource implements Dependency, Bom {

		public static final BomImport INSTANCE = new BomImport();

		private BomImport() {
		}

		@Override
		public String toString() {
			return "BOM";
		}

	}

	private static class ProfileBomImport extends DeclarationSource implements Dependency, Profile, Bom {

		private final String profileId;

		ProfileBomImport(String profileId) {
			this.profileId = profileId;
		}

		public String getProfileId() {
			return profileId;
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

	private static class Plugins extends DeclarationSource implements Plugin {

		public static final Plugins INSTANCE = new Plugins();

		private Plugins() {
		}

		@Override
		public String toString() {
			return "PL";
		}

	}

	private static class PluginManagement extends DeclarationSource implements Plugin, Managed {

		public static final PluginManagement INSTANCE = new PluginManagement();

		private PluginManagement() {
		}

		@Override
		public String toString() {
			return "PM";
		}

	}

	private static class ProfilePlugins extends DeclarationSource implements Plugin, Profile {

		private final String profileId;

		private ProfilePlugins(String profileId) {
			this.profileId = profileId;
		}

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


	private static class ProfilePluginManagement extends DeclarationSource implements Plugin, Profile, Managed {

		private final String profileId;

		private ProfilePluginManagement(String profileId) {
			this.profileId = profileId;
		}

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
