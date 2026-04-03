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

import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

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

	private final @Tag @XCollection(propertyElementName = "properties", elementName = "property",
			style = XCollection.Style.v2) List<Property> properties = new ArrayList<>();

	@Transient private final Map<String, Property> propertyMap = new java.util.TreeMap<>();

	public ProjectCache() {}

	public ProjectCache(ProjectId identity) {
		this.artifactId = identity.artifactId();
		this.groupId = identity.groupId();
		this.descriptor = identity.buildFile();
	}

	public @Nullable String getArtifactId() {
		return artifactId;
	}

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

	@Transient
	public String getSafeGroupId() {
		return StringUtils.hasText(groupId) ? groupId : "";
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public @Nullable String getDescriptor() {
		return descriptor;
	}

	@Transient
	public String getSafeDescriptor() {
		return StringUtils.hasText(descriptor) ? descriptor : "";
	}

	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	/**
	 * Returns all known property-to-artifact mappings. Each {@link Property} carries the property name and the
	 * artifact(s) whose version it controls.
	 */
	public List<Property> getProperties() {
		return List.copyOf(properties);
	}

	/**
	 * Update the cache with the given properties.
	 */
	@Transient
	public void setProperties(DependencyCollector collector) {

		this.properties.clear();
		this.propertyMap.clear();
		synchronized (this) {

			for (Dependency candidate : collector.getDependencies()) {

				for (VersionSource versionSource : candidate.getVersionSources()) {
					if (versionSource instanceof VersionSource.VersionPropertySource vps) {

						Property property = propertyMap.computeIfAbsent(vps.getProperty(), Property::new);
						property.setUsed(true);
						property.addArtifact(candidate.getArtifactId());
					}
				}
			}

			collector.doWithArtifacts((artifactId, usage) -> {

				if (usage.version() instanceof VersionSource.VersionPropertySource vps) {

					Property property = propertyMap.computeIfAbsent(vps.getProperty(), Property::new);
					property.addArtifact(artifactId);
				}
			});

			this.properties.addAll(propertyMap.values());

			for (String propertyName : collector.getProperties()) {
				propertyMap.computeIfAbsent(propertyName, k -> {
					Property property = new Property(k);
					this.properties.add(property);
					return property;
				}).setDeclared(true);
			}
		}
	}

	@Transient
	public @Nullable Property getProperty(String propertyName) {

		if (propertyMap.size() != properties.size()) {

			propertyMap.clear();
			for (Property property : properties) {
				propertyMap.put(property.name(), property);
			}
		}

		return propertyMap.get(propertyName);
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

	@Override
	public int compare(ProjectCache o1, ProjectCache o2) {
		return COMPARATOR.compare(o1, o2);
	}

	public ProjectId getId() {
		return ProjectId.of(groupId, artifactId, descriptor);
	}

}
