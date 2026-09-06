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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
 * Persistent property-to-artifact correlations for one {@link ProjectId}.
 *
 * @author Mark Paluch
 */
@Tag("project")
public class ProjectCache {

	static final Comparator<ProjectCache> COMPARATOR = Comparator.comparing(ProjectCache::getSafeGroupId)
			.thenComparing(ProjectCache::getSafeArtifactId).thenComparing(ProjectCache::getSafeDescriptor);

	private @Attribute @Nullable String artifactId;

	private @Attribute @Nullable String groupId;

	private @Attribute @Nullable String descriptor;

	@Attribute
	private long lastSeen = 0L;

	private final @Tag @XCollection(propertyElementName = "properties", elementName = "property", style = XCollection.Style.v2) List<VersionProperty> properties = new ArrayList<>();

	@Transient
	private final Map<String, VersionProperty> propertyMap = new TreeMap<>();

	/**
	 * Create an empty cache entry for XML serialization.
	 */
	public ProjectCache() {
	}

	public ProjectCache(ProjectId identity) {
		this.artifactId = identity.artifactId();
		this.groupId = identity.groupId();
		this.descriptor = identity.buildFile();
	}

	public @Nullable String getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the identifier, or an empty string if absent.
	 */
	@Transient
	public String getSafeArtifactId() {
		return StringUtils.hasText(artifactId) ? artifactId : "";
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public @Nullable String getGroupId() {
		return groupId;
	}

	/**
	 * Return the identifier, or an empty string if absent.
	 */
	@Transient
	public String getSafeGroupId() {
		return StringUtils.hasText(groupId) ? groupId : "";
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	/**
	 * Return the descriptor that distinguishes this project, typically its build
	 * file path.
	 */
	public @Nullable String getDescriptor() {
		return descriptor;
	}

	/**
	 * Return the descriptor, or an empty string if absent.
	 */
	@Transient
	public String getSafeDescriptor() {
		return StringUtils.hasText(descriptor) ? descriptor : "";
	}

	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	/**
	 * Return when this project entry was last populated.
	 *
	 * @return the epoch-millisecond write timestamp, or {@code 0} for a legacy
	 * entry that must not be expired by age.
	 */
	public long getLastSeen() {
		return lastSeen;
	}

	/**
	 * Return an immutable list containing live property entries.
	 */
	public synchronized List<VersionProperty> getProperties() {
		return List.copyOf(properties);
	}

	/**
	 * Replace property correlations, including declared properties that have no
	 * known artifact use.
	 *
	 * @param timestamp the write time in epoch milliseconds.
	 */
	@Transient
	public synchronized void setProperties(DependencyCollector collector, long timestamp) {

		this.properties.clear();
		this.propertyMap.clear();

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

		this.lastSeen = timestamp;
	}

	/**
	 * Find a property by name, or {@code null} if unknown.
	 */
	@Transient
	public synchronized @Nullable VersionProperty getProperty(String propertyName) {

		if (propertyMap.size() != properties.size()) {

			propertyMap.clear();
			for (VersionProperty property : properties) {
				propertyMap.put(property.name(), property);
			}
		}

		return propertyMap.get(propertyName);
	}

	public ProjectId getId() {
		return ProjectId.of(groupId, artifactId, descriptor);
	}

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

	/**
	 * Copy for persistence, including the mutable property entries.
	 */
	synchronized ProjectCache snapshot() {

		ProjectCache copy = new ProjectCache();
		copy.artifactId = this.artifactId;
		copy.groupId = this.groupId;
		copy.descriptor = this.descriptor;
		copy.lastSeen = this.lastSeen;
		for (VersionProperty property : this.properties) {
			copy.properties.add(property.snapshot());
		}
		return copy;
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
