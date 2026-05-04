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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Per-project cache entry that stores property-to-artifact mappings.
 *
 * @author Mark Paluch
 */
@Tag("project")
public class ProjectCache implements Comparator<ProjectCache> {

	static final Comparator<ProjectCache> COMPARATOR = Comparator.comparing(ProjectCache::getSafeGroupId)
			.thenComparing(ProjectCache::getSafeArtifactId).thenComparing(ProjectCache::getSafeDescriptor);

	private @Attribute @Nullable String artifactId;

	private @Attribute @Nullable String groupId;

	private @Attribute @Nullable String descriptor;

	private final @Tag @XCollection(propertyElementName = "properties", elementName = "property", style = XCollection.Style.v2) List<VersionProperty> properties = new ArrayList<>();

	@Transient
	private final Map<String, VersionProperty> propertyMap = new java.util.TreeMap<>();

	/**
	 * Create an empty cache entry for XML serialization.
	 */
	public ProjectCache() {
	}

	/**
	 * Create a cache entry for the given project identity.
	 */
	public ProjectCache(ProjectId identity) {
		this.artifactId = identity.artifactId();
		this.groupId = identity.groupId();
		this.descriptor = identity.buildFile();
	}

	/**
	 * Return the cached artifact identifier of the owning project, if known.
	 *
	 * @return the project artifact identifier, or {@code null}.
	 */
	public @Nullable String getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the cached artifact identifier or an empty string if absent.
	 *
	 * @return a non-{@code null} artifact identifier representation.
	 */
	@Transient
	public String getSafeArtifactId() {
		return StringUtils.hasText(artifactId) ? artifactId : "";
	}

	/**
	 * Set the cached artifact identifier of the owning project.
	 *
	 * @param artifactId the artifact identifier to store.
	 */
	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	/**
	 * Return the cached group identifier of the owning project, if known.
	 *
	 * @return the project group identifier, or {@code null}.
	 */
	public @Nullable String getGroupId() {
		return groupId;
	}

	/**
	 * Return the cached group identifier or an empty string if absent.
	 *
	 * @return a non-{@code null} group identifier representation.
	 */
	@Transient
	public String getSafeGroupId() {
		return StringUtils.hasText(groupId) ? groupId : "";
	}

	/**
	 * Set the cached group identifier of the owning project.
	 *
	 * @param groupId the group identifier to store.
	 */
	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	/**
	 * Return the descriptor used to distinguish the owning project entry, typically
	 * the build file path.
	 *
	 * @return the descriptor, or {@code null}.
	 */
	public @Nullable String getDescriptor() {
		return descriptor;
	}

	/**
	 * Return the descriptor or an empty string if absent.
	 *
	 * @return a non-{@code null} descriptor representation.
	 */
	@Transient
	public String getSafeDescriptor() {
		return StringUtils.hasText(descriptor) ? descriptor : "";
	}

	/**
	 * Set the descriptor used to distinguish the owning project entry.
	 *
	 * @param descriptor the descriptor to store.
	 */
	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	/**
	 * Returns all known property-to-artifact mappings. Each {@link VersionProperty}
	 * carries the property name and the artifact(s) whose version it controls.
	 */
	public List<VersionProperty> getProperties() {
		return List.copyOf(properties);
	}

	/**
	 * Set this project's property correlations from the given dependency collector.
	 * <p>The resulting property set contains both:
	 * <ul>
	 * <li>properties that are used as version sources for one or more declarations,
	 * and</li>
	 * <li>properties that are merely declared in the project and therefore have no
	 * associated artifacts yet.</li>
	 * </ul>
	 *
	 * @param collector the collector whose declarations and properties should be
	 * used.
	 */
	@Transient
	public void setProperties(DependencyCollector collector) {

		this.properties.clear();
		this.propertyMap.clear();

		synchronized (this) {

			for (DeclaredDependency declaration : collector.getDeclarations()) {

				for (VersionSource versionSource : declaration.getVersionSources()) {
					if (versionSource instanceof VersionSource.VersionProperty vps) {

						VersionProperty property = propertyMap.computeIfAbsent(vps.getProperty(), VersionProperty::new);
						property.setUsed(true);
						property.addArtifact(declaration.getArtifactId());
					}
				}
			}

			this.properties.addAll(propertyMap.values());

			for (String propertyName : collector.getProperties()) {
				propertyMap.computeIfAbsent(propertyName, k -> {
					VersionProperty property = new VersionProperty(k);
					this.properties.add(property);
					return property;
				}).setDeclared(true);
			}
		}
	}

	/**
	 * Return the cached property with the given name.
	 * <p>If this instance was deserialized and the transient lookup map is not yet
	 * in sync with the persisted list, the lookup map is rebuilt first.
	 *
	 * @param propertyName the property name.
	 * @return the matching property, or {@code null} if none is known.
	 */
	@Transient
	public @Nullable VersionProperty getProperty(String propertyName) {

		if (propertyMap.size() != properties.size()) {

			propertyMap.clear();
			for (VersionProperty property : properties) {
				propertyMap.put(property.name(), property);
			}
		}

		return propertyMap.get(propertyName);
	}

	/**
	 * Return this cache entry's project identity.
	 *
	 * @return the corresponding project identity.
	 */
	public ProjectId getId() {
		return ProjectId.of(groupId, artifactId, descriptor);
	}

	/**
	 * Return whether this entry represents the given project identity.
	 *
	 * @param identity the project identity to compare with.
	 * @return {@literal true} if group, artifact, and descriptor all match.
	 */
	public boolean matches(ProjectId identity) {

		if (!ObjectUtils.nullSafeEquals(this.descriptor, identity.buildFile())) {
			return false;
		}

		if (!ObjectUtils.nullSafeEquals(this.groupId, identity.groupId())) {
			return false;
		}

		if (!ObjectUtils.nullSafeEquals(this.artifactId, identity.artifactId())) {
			return false;
		}

		return true;
	}

	@Override
	public int compare(ProjectCache o1, ProjectCache o2) {
		return COMPARATOR.compare(o1, o2);
	}

	@Override
	public String toString() {
		return "ProjectCache{" +
				"artifactId='" + artifactId + '\'' +
				", groupId='" + groupId + '\'' +
				", descriptor='" + descriptor + '\'' +
				'}';
	}

}
