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
import biz.paluch.dap.artifact.VersionOption;

import java.util.ArrayList;
import java.util.List;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

@Tag("artifact")
public class Artifact {

	private @Attribute String groupId;
	private @Attribute String artifactId;

	private final @XCollection(propertyElementName = "releases", elementName = "release",
			style = XCollection.Style.v2) List<Release> releases = new ArrayList<>();

	public Artifact() {}

	public Artifact(String groupId, String artifactId) {
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	public Artifact(ArtifactId artifactId) {
		this(artifactId.groupId(), artifactId.artifactId());
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public List<Release> getReleases() {
		return releases;
	}

	public boolean matches(ArtifactId artifactId) {
		return getArtifactId().equals(artifactId.artifactId()) && getGroupId().equals(artifactId.groupId());
	}

	@Transient
	public List<VersionOption> getVersionOptions() {

		List<VersionOption> options = new ArrayList<>();
		for (Release release : releases) {
			options.add(release.toVersionOption());
		}
		return options;
	}

	public void setVersionOptions(List<VersionOption> versionOptions) {
		this.releases.clear();
		for (VersionOption versionOption : versionOptions) {
			this.releases.add(Release.from(versionOption));
		}
	}

	public ArtifactId toArtifactId() {
		return new ArtifactId(groupId, artifactId);
	}
}
