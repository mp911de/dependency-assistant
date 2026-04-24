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
import java.util.List;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * Persistent descriptor of a project property and the artifacts it governs.
 * <p>A property can be merely declared, actually used as a version source, or
 * both. Artifact associations are stored without duplicates.
 *
 * @author Mark Paluch
 */
@Tag("property")
public class Property {

	@Attribute private String name;
	@Attribute private boolean declared;
	@Attribute private boolean used;
	@XCollection(propertyElementName = "artifacts", elementName = "artifact",
			style = XCollection.Style.v2) private final List<CachedArtifact> artifacts = new ArrayList<>();

	/**
	 * Create an empty property entry for XML deserialization.
	 */
	public Property() {}

	/**
	 * Create a property entry with the given name and no artifact associations.
	 *
	 * @param name the property name.
	 */
	public Property(String name) {
		this(name, new ArrayList<>());
	}

	/**
	 * Create a property entry with the given name and artifact associations.
	 *
	 * @param name the property name.
	 * @param artifacts the initial artifact associations.
	 */
	public Property(String name, List<CachedArtifact> artifacts) {
		this.name = name;
		synchronized (this.artifacts) {
			this.artifacts.addAll(artifacts);
		}
	}

	/**
	 * Associate this property with the given artifact unless such an association is
	 * already present.
	 *
	 * @param artifactId the artifact to associate.
	 */
	public void addArtifact(ArtifactId artifactId) {

		synchronized (this.artifacts) {

			for (CachedArtifact artifact : artifacts) {
				if (artifact.matches(artifactId)) {
					return;
				}
			}

			this.artifacts.add(new CachedArtifact(artifactId));
		}
	}

	/**
	 * Return whether this property is currently used as a version source.
	 *
	 * @return {@code true} if the property is used.
	 */
	public boolean isUsed() {
		return used;
	}

	/**
	 * Mark whether this property is used as a version source.
	 *
	 * @param used whether the property is used.
	 */
	public void setUsed(boolean used) {
		this.used = used;
	}

	/**
	 * Return whether this property is declared in the analyzed project.
	 *
	 * @return {@code true} if the property is declared.
	 */
	public boolean isDeclared() {
		return declared;
	}

	/**
	 * Mark whether this property is declared in the analyzed project.
	 *
	 * @param declared whether the property is declared.
	 */
	public void setDeclared(boolean declared) {
		this.declared = declared;
	}

	/**
	 * Return the property name.
	 *
	 * @return the property name.
	 */
	@Tag
	public String name() {
		return name;
	}

	/**
	 * Return the backing artifact associations.
	 * <p>This is the live storage list used for persistence and in-place mutation.
	 *
	 * @return the mutable backing artifact associations.
	 */
	public List<CachedArtifact> artifacts() {
		return artifacts;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		Property that = (Property) obj;
		return Objects.equals(this.name, that.name) && Objects.equals(this.artifacts, that.artifacts);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, artifacts);
	}

	@Override
	public String toString() {
		return "Property[" + "name=" + name + ", " + "artifacts=" + artifacts + ']';
	}

	/**
	 * Return whether this property is associated with at least one artifact.
	 *
	 * @return {@code true} if at least one artifact association is present.
	 */
	public boolean hasArtifacts() {
		return !artifacts.isEmpty();
	}

}
