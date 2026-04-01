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
 * Where in the POM a dependency is declared.
 */
public abstract class DeclarationSource {

	/**
	 * Declaration source for a plugin.
	 */
	public interface Plugin {

	}

	/**
	 * Declaration source for a dependency.
	 */
	public interface Dependency {

	}

	/**
	 * Declaration source for a managed dependency.
	 */
	public interface Managed {

	}

	/**
	 * Artifact declared within a profile.
	 */
	public interface Profile {
		String getProfileId();
	}

	/**
	 * Direct dependency.
	 */
	public static DeclarationSource dependency() {
		return Dependencies.INSTANCE;
	}

	/**
	 * Managed dependency.
	 */
	public static DeclarationSource managed() {
		return DependencyManagement.INSTANCE;
	}

	/**
	 * Dependency under a profile.
	 */
	public static DeclarationSource profileDependency(String id) {
		return new ProfileDependencies(id);
	}

	/**
	 * Managed dependency under a profile.
	 */
	public static DeclarationSource profileManaged(String id) {
		return new ProfileDependencyManagement(id);
	}

	/**
	 * Direct plugin.
	 */
	public static DeclarationSource plugin() {
		return Plugins.INSTANCE;
	}

	/**
	 * Managed plugin.
	 */
	public static DeclarationSource pluginManagement() {
		return PluginManagement.INSTANCE;
	}

	/**
	 * Direct plugin under a profile.
	 */
	public static DeclarationSource profilePlugin(String id) {
		return new ProfilePlugins(id);
	}

	/**
	 * Managed plugin under a profile.
	 */
	public static DeclarationSource profilePluginManagement(String id) {
		return new ProfilePluginManagement(id);
	}

	@Override
	public abstract String toString();

	/** Dependencies under project/dependencies. */
	private static class Dependencies extends DeclarationSource implements Dependency {

		public static final Dependencies INSTANCE = new Dependencies();

		private Dependencies() {}

		@Override
		public String toString() {
			return "DP";
		}
	}

	/** Dependencies under project/dependencyManagement. */
	private static class DependencyManagement extends DeclarationSource implements Dependency, Managed {

		public static final DependencyManagement INSTANCE = new DependencyManagement();

		private DependencyManagement() {}

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

		private Plugins() {}

		@Override
		public String toString() {
			return "PL";
		}

	}

	/** Plugins under project/build/pluginManagement. */
	private static class PluginManagement extends DeclarationSource implements Plugin, Managed {

		public static final PluginManagement INSTANCE = new PluginManagement();

		private PluginManagement() {}

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
