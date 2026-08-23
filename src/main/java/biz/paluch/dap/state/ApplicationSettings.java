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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.Consumer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Application-level persistent settings for Dependency Assistant.
 *
 * <p>The service retains the plugin version used for update notifications,
 * rename-dialog preferences, and remembered display names for package
 * constellations. These settings are shared by all projects and persisted in
 * {@code dependency-assistant.xml}.
 *
 * <p>Name hints are matched by {@link PackageIdentity} and favor the most
 * recently stored matching hint.
 *
 * @author Mark Paluch
 */
@State(name = "DependencyAssistantApplicationSettings", storages = @Storage(value = "dependency-assistant.xml", exportable = true))
public class ApplicationSettings implements PersistentStateComponent<ApplicationSettings.State>, ModificationTracker {

	private State state = new State();

	private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

	/**
	 * Return the application-level settings service.
	 *
	 * @return the shared settings service.
	 */
	@NotNull
	public static ApplicationSettings getInstance() {
		return ApplicationManager.getApplication().getService(ApplicationSettings.class);
	}

	/**
	 * Return a snapshot of the state managed by IntelliJ persistence.
	 *
	 * <p>The snapshot decouples platform serialization from concurrent name-hint
	 * updates.
	 *
	 * @return the persistent settings snapshot.
	 */
	@Override
	public State getState() {
		return doWithState(State::snapshot);
	}

	/**
	 * Copy settings loaded by IntelliJ persistence into this service.
	 *
	 * @param state the persisted settings to load.
	 */
	@Override
	public void loadState(State state) {
		doWithState(it -> {
			this.state = state;
		});
	}

	@Override
	public long getModificationCount() {
		return modificationTracker.getModificationCount();
	}

	/**
	 * Return the plugin version for which the update notification was last
	 * evaluated.
	 *
	 * @return the recorded plugin version, or {@literal null} before a version has
	 * been recorded.
	 */
	public @Nullable String getVersion() {
		return state.getPluginVersion();
	}

	/**
	 * Record the plugin version used to evaluate update notifications.
	 *
	 * @param version the current plugin version.
	 */
	public void setVersion(String version) {
		modificationTracker.incModificationCount();
		state.setPluginVersion(version);
	}

	/**
	 * Determine whether the rename dialog preselects "remember name".
	 *
	 * <p>The setting records the most recent accepted choice and defaults to
	 * {@code true}.
	 *
	 * @return {@code true} if the choice is preselected.
	 */
	public boolean isRememberRenamedNames() {
		return state.isRememberRenamedNames();
	}

	/**
	 * Set whether subsequent rename dialogs preselect "remember name".
	 *
	 * @param rememberRenamedNames whether the choice is preselected.
	 */
	public void setRememberRenamedNames(boolean rememberRenamedNames) {
		modificationTracker.incModificationCount();
		state.setRememberRenamedNames(rememberRenamedNames);
	}

	/**
	 * Determine whether the rename dialog preselects "update dependencyfile.json".
	 *
	 * <p>The setting records the most recent accepted choice made while a
	 * descriptor was available and defaults to {@code false}.
	 *
	 * @return {@code true} if the choice is preselected.
	 */
	public boolean isUpdateDependencyfileOnRename() {
		return state.isUpdateDependencyfileOnRename();
	}

	/**
	 * Set whether subsequent rename dialogs preselect "update dependencyfile.json".
	 *
	 * @param updateDependencyfileOnRename whether the choice is preselected.
	 */
	public void setUpdateDependencyfileOnRename(boolean updateDependencyfileOnRename) {
		modificationTracker.incModificationCount();
		state.setUpdateDependencyfileOnRename(updateDependencyfileOnRename);
	}

	/**
	 * Find the most recently stored name for the single-package constellation
	 * containing the given package.
	 *
	 * @param pkg the package identity whose name to find.
	 * @return the remembered name, or {@literal null} if no hint matches.
	 * @see #findNameHint(List)
	 */
	public @Nullable String findNameHint(PackageIdentity pkg) {
		List<String> nameHints = getNameHints(List.of(pkg));
		return nameHints.isEmpty() ? null : nameHints.getFirst();
	}

	/**
	 * Find the most recently stored name for the given package constellation.
	 *
	 * <p>Package order does not affect matching. A hint matches only the complete
	 * constellation for which it was stored.
	 *
	 * @param packages the package identities whose remembered name to find.
	 * @return the remembered name, or {@literal null} if no hint matches.
	 */
	public @Nullable String findNameHint(List<PackageIdentity> packages) {
		List<String> nameHints = getNameHints(packages);
		return nameHints.isEmpty() ? null : nameHints.getFirst();
	}

	private List<String> getNameHints(Collection<PackageIdentity> packages) {
		return doWithState(state -> {

			List<String> result = new ArrayList<>();
			for (NameHint nameHint : state.nameHints) {
				if (nameHint.matches(packages)) {
					result.add(nameHint.name);
				}
			}
			Collections.reverse(result);
			return result;
		});
	}

	/**
	 * Remember a name for a single-package constellation.
	 *
	 * @param name the name to remember.
	 * @param packages the package identity that forms the constellation.
	 * @see #addNameHint(String, List)
	 */
	public void addNameHint(String name, PackageIdentity packages) {
		addNameHint(name, List.of(packages));
	}

	/**
	 * Remember a name for the given package constellation.
	 *
	 * <p>The package identities are copied into persistent state. This entry takes
	 * precedence over older matching entries.
	 *
	 * @param name the name to remember.
	 * @param packages the package identities that form the constellation.
	 * @return whether the hint was added. Returns {@code false} if the hint already
	 * exists with the name.
	 */
	public boolean addNameHint(String name, List<PackageIdentity> packages) {

		return doWithState(state -> {

			String existing = findNameHint(packages);
			if (name.equals(existing)) {
				return false;
			}

			modificationTracker.incModificationCount();
			state.nameHints.add(NameHint.of(name, packages));
			return true;
		});
	}

	/**
	 * Remove all occurrences of a remembered name for the given package
	 * constellation.
	 *
	 * <p>The operation has no effect when no stored hint matches both arguments.
	 *
	 * @param name the remembered name to remove.
	 * @param packages the package identities that form the constellation.
	 */
	public void removeNameHint(String name, List<PackageIdentity> packages) {
		doWithState(state -> {
			if (state.nameHints.removeIf(it -> it.matches(name, packages))) {
				modificationTracker.incModificationCount();
			}
		});
	}

	public void doWithState(Consumer<? super State> action) {
		State state = this.state;
		synchronized (state.mutex) {
			action.accept(state);
		}
	}

	public <T> T doWithState(Function<? super State, ? extends T> action) {
		State state = this.state;
		synchronized (state.mutex) {
			return action.apply(state);
		}
	}

	/**
	 * XML-serializable state value for {@link ApplicationSettings}.
	 *
	 * <p>Instances returned by {@link ApplicationSettings#getState()} are detached
	 * from the live service. Use {@code ApplicationSettings} methods to update live
	 * settings.
	 */
	public static class State {

		final @Transient Object mutex = new Object();

		private @Attribute("plugin-version") @Nullable String pluginVersion;

		private @Attribute boolean rememberRenamedNames = true;

		private @Attribute boolean updateDependencyfileOnRename;

		private final @XCollection(propertyElementName = "name-hints", elementName = "name-hint", style = XCollection.Style.v2) List<NameHint> nameHints = new ArrayList<>();

		public @Nullable String getPluginVersion() {
			return pluginVersion;
		}

		public void setPluginVersion(String pluginVersion) {
			this.pluginVersion = pluginVersion;
		}

		public boolean isRememberRenamedNames() {
			return rememberRenamedNames;
		}

		public void setRememberRenamedNames(boolean rememberRenamedNames) {
			this.rememberRenamedNames = rememberRenamedNames;
		}

		public boolean isUpdateDependencyfileOnRename() {
			return updateDependencyfileOnRename;
		}

		public void setUpdateDependencyfileOnRename(boolean updateDependencyfileOnRename) {
			this.updateDependencyfileOnRename = updateDependencyfileOnRename;
		}

		public List<NameHint> getNameHints() {
			return nameHints;
		}

		/**
		 * Create a detached copy for persistence.
		 *
		 * @return a snapshot of this state.
		 */
		public State snapshot() {

			State state = new State();
			state.pluginVersion = this.pluginVersion;
			state.rememberRenamedNames = this.rememberRenamedNames;
			state.updateDependencyfileOnRename = this.updateDependencyfileOnRename;

			List<NameHint> copy = new ArrayList<>(this.nameHints);
			Collections.reverse(copy);

			Set<NameHint> unique = new LinkedHashSet<>(copy);
			state.getNameHints().addAll(unique);
			Collections.reverse(state.getNameHints());

			return state;
		}

	}

	/**
	 * XML-serializable association between a remembered name and a package
	 * constellation.
	 * <p>Equality check considers only {@code artifacts} and ignores {@code name}.
	 */
	@Tag("name-hint")
	static class NameHint {

		private @Attribute String name;

		private final @XCollection(propertyElementName = "artifacts", elementName = "artifact", style = XCollection.Style.v2) Set<Artifact> artifacts = new TreeSet<>();

		public NameHint() {
		}

		public NameHint(String name) {
			this.name = name;
		}

		public static NameHint of(String name, List<PackageIdentity> packages) {

			NameHint nameHint = new NameHint(name);
			for (PackageIdentity pkg : packages) {
				nameHint.artifacts.add(Artifact.of(pkg));
			}
			return nameHint;
		}

		public String getName() {
			return name;
		}

		public Collection<Artifact> getArtifacts() {
			return artifacts;
		}

		public boolean matches(String name, List<PackageIdentity> packages) {
			if (name.equals(this.name) && matches(packages)) {
				return true;
			}
			return false;
		}

		public boolean matches(Collection<PackageIdentity> packages) {

			if (packages.size() == artifacts.size()) {
				for (Artifact artifact : artifacts) {
					boolean hasMatch = false;
					for (PackageIdentity pkg : packages) {
						if (artifact.matches(pkg)) {
							hasMatch = true;
							break;
						}
					}

					if (!hasMatch) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof NameHint nameHint)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(artifacts, nameHint.artifacts);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(artifacts);
		}

	}

	/**
	 * XML-serializable representation of a {@link PackageIdentity} stored in a name
	 * hint.
	 */
	@Tag("artifact")
	public static class Artifact implements Comparable<Artifact> {

		private static final Comparator<Artifact> COMPARATOR = Comparator.comparing(Artifact::getPackageSystem)
				.thenComparing(Artifact::getGroupId)
				.thenComparing(Artifact::getArtifactId);

		private @Attribute PackageSystem packageSystem;

		private @Attribute String groupId;

		private @Attribute String artifactId;

		public Artifact() {
		}

		public Artifact(PackageSystem packageSystem, String groupId, String artifactId) {
			this.packageSystem = packageSystem;
			this.groupId = groupId;
			this.artifactId = artifactId;
		}

		public static Artifact of(PackageIdentity pkg) {
			ArtifactId artifactId = pkg.getArtifactId();
			return new Artifact(pkg.getPackageSystem(), artifactId.groupId(), artifactId.artifactId());
		}

		public PackageSystem getPackageSystem() {
			return packageSystem;
		}

		public void setPackageSystem(PackageSystem packageSystem) {
			this.packageSystem = packageSystem;
		}

		public String getGroupId() {
			return groupId;
		}

		public void setGroupId(String groupId) {
			this.groupId = groupId;
		}

		public String getArtifactId() {
			return artifactId;
		}

		public void setArtifactId(String artifactId) {
			this.artifactId = artifactId;
		}

		public boolean matches(PackageIdentity pkg) {
			ArtifactId artifactId = pkg.getArtifactId();
			return this.packageSystem == pkg.getPackageSystem() && this.groupId.equals(artifactId.groupId())
					&& this.artifactId.equals(artifactId.artifactId());
		}

		@Override
		public int compareTo(Artifact o) {
			return COMPARATOR.compare(this, o);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Artifact artifact)) {
				return false;
			}
			if (packageSystem != artifact.packageSystem) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(groupId, artifact.groupId)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(artifactId, artifact.artifactId);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(
					packageSystem, groupId, artifactId);
		}

	}

}
