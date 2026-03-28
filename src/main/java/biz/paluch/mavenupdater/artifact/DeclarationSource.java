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
package biz.paluch.mavenupdater.artifact;

import org.jspecify.annotations.Nullable;

/**
 * Where in the POM a dependency is declared.
 */
public abstract class DeclarationSource {

	public static DeclarationSource managed() {
		return DependencyManagement.INSTANCE;
	}

	public static DeclarationSource dependency() {
		return Dependencies.INSTANCE;
	}

	public static DeclarationSource profileDependency(String id) {
		return new ProfileDependencies(id);
	}

	public static DeclarationSource profileManaged(String id) {
		return new ProfileDependencyManagement(id);
	}

	public static DeclarationSource pluginManagement() {
		return PluginManagement.INSTANCE;
	}

	public static DeclarationSource plugin() {
		return Plugins.INSTANCE;
	}

	public static DeclarationSource profilePluginManagement(String id) {
		return new ProfilePluginManagement(id);
	}

	public static DeclarationSource profilePlugin(String id) {
		return new ProfilePlugins(id);
	}

	@Override
	public abstract String toString();

	/** Dependencies under project/dependencies. */
	public static final class Dependencies extends DeclarationSource implements Dependency {

		public static final Dependencies INSTANCE = new Dependencies();

		private Dependencies() {}

		@Override
		public String toString() {
			return "DP";
		}
	}

	/** Dependencies under project/dependencyManagement. */
	public static final class DependencyManagement extends DeclarationSource implements Dependency {

		public static final DependencyManagement INSTANCE = new DependencyManagement();

		private DependencyManagement() {}

		@Override
		public String toString() {
			return "DM";
		}
	}

	/** Dependencies under a profile's dependencies. */
	public static final class ProfileDependencies extends DeclarationSource implements Dependency, Profile {

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
	public static final class ProfileDependencyManagement extends DeclarationSource implements Dependency, Profile {

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
	public static final class Plugins extends DeclarationSource implements Plugin {

		public static final Plugins INSTANCE = new Plugins();

		private Plugins() {}

		@Override
		public String toString() {
			return "PL";
		}
	}

	/** Plugins under project/build/pluginManagement. */
	public static final class PluginManagement extends DeclarationSource implements Plugin {

		public static final PluginManagement INSTANCE = new PluginManagement();

		private PluginManagement() {}

		@Override
		public String toString() {
			return "PM";
		}
	}

	/** Plugins under a profile's build/plugins. */
	public static final class ProfilePlugins extends DeclarationSource implements Plugin, Profile {

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
	public static final class ProfilePluginManagement extends DeclarationSource implements Plugin, Profile {

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

	public interface Plugin {

	}

	public interface Dependency {

	}

	public interface Profile {
		String getProfileId();
	}
}
