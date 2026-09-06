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
import java.util.List;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * Persistent property correlations. A property may be declared, used as a
 * version source, or both.
 *
 * @author Mark Paluch
 */
@Tag("property")
public class VersionProperty {

	@Attribute
	private String name;

	@Attribute
	private boolean declared;

	@Attribute
	private boolean used;

	@XCollection(propertyElementName = "artifacts", elementName = "artifact", style = XCollection.Style.v2)
	private final List<CachedArtifact> artifacts = new ArrayList<>();

	/**
	 * Create an empty property entry for XML deserialization.
	 */
	public VersionProperty() {
	}

	public VersionProperty(String name) {
		this(name, new ArrayList<>());
	}

	public VersionProperty(String name, CachedArtifact... artifacts) {
		this.name = name;
		this.artifacts.addAll(List.of(artifacts));
	}

	public VersionProperty(String name, List<CachedArtifact> artifacts) {
		this.name = name;
		this.artifacts.addAll(artifacts);
	}

	/**
	 * Add an association if its coordinates are not already present.
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
	 * Whether the property is used as a version source.
	 */
	public boolean isUsed() {
		return used;
	}

	public void setUsed(boolean used) {
		this.used = used;
	}

	public boolean isDeclared() {
		return declared;
	}

	public void setDeclared(boolean declared) {
		this.declared = declared;
	}

	@Tag
	public String name() {
		return name;
	}

	/**
	 * Return the mutable backing list of artifact associations.
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
		VersionProperty that = (VersionProperty) obj;
		return Objects.equals(this.name, that.name) && Objects.equals(this.artifacts, that.artifacts);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, artifacts);
	}

	@Override
	public String toString() {
		return "VersionProperty[" + "name=" + name + ", " + "artifacts=" + artifacts + ']';
	}

	public boolean hasArtifacts() {
		return !artifacts.isEmpty();
	}

	/**
	 * Copy for persistence with an independent association list.
	 */
	VersionProperty snapshot() {

		VersionProperty copy;
		synchronized (this.artifacts) {
			copy = new VersionProperty(this.name, new ArrayList<>(this.artifacts));
		}
		copy.declared = this.declared;
		copy.used = this.used;
		return copy;
	}

}
