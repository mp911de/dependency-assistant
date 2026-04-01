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

import biz.paluch.dap.artifact.ArtifactId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

@Tag("property")
public class Property {

	@Attribute private String name;
	@XCollection(propertyElementName = "artifacts", elementName = "artifact",
			style = XCollection.Style.v2) private final List<CachedArtifact> artifacts = new ArrayList<>();

	public Property() {}

	public Property(String name) {
		this(name, new ArrayList<>());
	}

	public Property(String name, List<CachedArtifact> artifacts) {
		this.name = name;
		this.artifacts.addAll(artifacts);
	}

	public void addArtifact(ArtifactId artifactId) {
		this.artifacts.add(new CachedArtifact(artifactId));
	}

	@Tag
	public String name() {
		return name;
	}

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
		var that = (Property) obj;
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

}
