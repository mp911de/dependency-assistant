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

import org.jspecify.annotations.Nullable;

/**
 * Identifies the structural location in a build file where a dependency or
 * plugin is declared.
 * <p>The class uses marker interfaces as independent classification axes:
 * {@link Dependency} vs {@link Plugin} identifies the artifact kind,
 * {@link Managed} indicates a version-constraint section rather than an active
 * use, and {@link Profile} marks a Maven profile scope.
 *
 * @author Mark Paluch
 * @see Managed
 * @see Profile
 * @see VersionSource
 * @see DeclaredDependency
 */
public abstract class DeclarationSource {

	/**
	 * Marker interface for a build plugin declaration (e.g. Maven
	 * {@code <build><plugins>} or Gradle {@code plugins {}}).
	 * <p>A source that is {@code instanceof Plugin} is never
	 * {@code instanceof Dependency}.
	 */
	public interface Plugin {

	}

	/**
	 * Marker interface for a library dependency declaration (e.g. Maven
	 * {@code <dependencies>} or a Gradle dependency configuration).
	 * <p>A source that is {@code instanceof Dependency} is never
	 * {@code instanceof Plugin}.
	 */
	public interface Dependency {

	}

	/**
	 * Marker interface for a version-constraint entry in a management section
	 * (Maven {@code <dependencyManagement>} or {@code <pluginManagement>}) rather
	 * than an active dependency or plugin use.
	 * <p>May be combined with {@link Dependency} or {@link Plugin}.
	 */
	public interface Managed {

	}

	/**
	 * Marker interface for a declaration scoped to a named Maven profile.
	 */
	public interface Profile {

		/**
		 * Return the identifier of the Maven profile that contains this declaration.
		 *
		 * @return the profile id; may be {@literal null} if the profile has no id.
		 */
		String getProfileId();

	}

	/**
	 * Return the source for a direct library dependency
	 * ({@code project/dependencies}).
	 *
	 * @return the singleton instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource dependency() {
		return Dependencies.INSTANCE;
	}

	/**
	 * Return the source for a managed library dependency
	 * ({@code project/dependencyManagement}).
	 *
	 * @return the singleton instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource managed() {
		return DependencyManagement.INSTANCE;
	}

	/**
	 * Return the source for a direct library dependency within the named Maven
	 * profile.
	 *
	 * @param id the profile identifier; can be {@literal null}.
	 * @return a new instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource profileDependency(String id) {
		return new ProfileDependencies(id);
	}

	/**
	 * Return the source for a managed library dependency within the named Maven
	 * profile.
	 *
	 * @param id the profile identifier; can be {@literal null}.
	 * @return a new instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource profileManaged(String id) {
		return new ProfileDependencyManagement(id);
	}

	/**
	 * Return the source for a direct plugin ({@code project/build/plugins}).
	 *
	 * @return the singleton instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource plugin() {
		return Plugins.INSTANCE;
	}

	/**
	 * Return the source for a managed plugin
	 * ({@code project/build/pluginManagement}).
	 *
	 * @return the singleton instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource pluginManagement() {
		return PluginManagement.INSTANCE;
	}

	/**
	 * Return the source for a direct plugin within the named Maven profile.
	 *
	 * @param id the profile identifier; can be {@literal null}.
	 * @return a new instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource profilePlugin(String id) {
		return new ProfilePlugins(id);
	}

	/**
	 * Return the source for a managed plugin within the named Maven profile.
	 *
	 * @param id the profile identifier; can be {@literal null}.
	 * @return a new instance; guaranteed to be not {@literal null}.
	 */
	public static DeclarationSource profilePluginManagement(String id) {
		return new ProfilePluginManagement(id);
	}

	@Override
	public abstract String toString();

	/** Dependencies under project/dependencies. */
	private static class Dependencies extends DeclarationSource implements Dependency {

		public static final Dependencies INSTANCE = new Dependencies();

		private Dependencies() {
		}

		@Override
		public String toString() {
			return "DP";
		}

	}

	/** Dependencies under project/dependencyManagement. */
	private static class DependencyManagement extends DeclarationSource implements Dependency, Managed {

		public static final DependencyManagement INSTANCE = new DependencyManagement();

		private DependencyManagement() {
		}

		@Override
		public String toString() {
			return "DM";
		}

	}

	/** Dependencies under a profile's dependencies. */
	private static class ProfileDependencies extends DeclarationSource implements Dependency, Profile {

		private final @Nullable String profileId;

		private ProfileDependencies(@Nullable String profileId) {
			this.profileId = profileId;
		}

		public @Nullable String getProfileId() {
			return profileId;
		}

		@Override
		public String toString() {
			return "profile:" + profileId;
		}

	}

	/** Dependencies under a profile's dependencyManagement. */
	private static class ProfileDependencyManagement extends DeclarationSource implements Dependency, Profile, Managed {

		private final @Nullable String profileId;

		private ProfileDependencyManagement(@Nullable String profileId) {
			this.profileId = profileId;
		}

		public @Nullable String getProfileId() {
			return profileId;
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/DM";
		}

	}

	/** Plugins under project/build/plugins. */
	private static class Plugins extends DeclarationSource implements Plugin {

		public static final Plugins INSTANCE = new Plugins();

		private Plugins() {
		}

		@Override
		public String toString() {
			return "PL";
		}

	}

	/** Plugins under project/build/pluginManagement. */
	private static class PluginManagement extends DeclarationSource implements Plugin, Managed {

		public static final PluginManagement INSTANCE = new PluginManagement();

		private PluginManagement() {
		}

		@Override
		public String toString() {
			return "PM";
		}

	}

	/** Plugins under a profile's build/plugins. */
	private static class ProfilePlugins extends DeclarationSource implements Plugin, Profile {

		private final @Nullable String profileId;

		private ProfilePlugins(@Nullable String profileId) {
			this.profileId = profileId;
		}

		public @Nullable String getProfileId() {
			return profileId;
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/PL";
		}

	}

	/** Plugins under a profile's build/pluginManagement. */
	private static class ProfilePluginManagement extends DeclarationSource implements Plugin, Profile, Managed {

		private final @Nullable String profileId;

		private ProfilePluginManagement(@Nullable String profileId) {
			this.profileId = profileId;
		}

		public @Nullable String getProfileId() {
			return profileId;
		}

		@Override
		public String toString() {
			return "profile:" + profileId + "/PM";
		}

	}

}
