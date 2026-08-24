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

package biz.paluch.dap.artifact;

/**
 * Package ecosystem that interprets dependency coordinates.
 *
 * <p>This classification is narrower than the build ecosystem. Maven and Gradle
 * declarations both resolve to {@link #MAVEN}, while GitHub Actions use
 * {@link #GITHUB}.
 *
 * @author Mark Paluch
 */
public enum PackageSystem {

	/**
	 * Maven coordinates declared in {@code pom.xml} and Gradle build files.
	 */
	MAVEN,

	/**
	 * NPM packages declared in {@code package.json} files.
	 */
	NPM {

		@Override
		public String getCoordinates(ArtifactId artifactId) {

			if (artifactId.groupId().equals(artifactId.artifactId())) {
				return artifactId.artifactId();
			}

			String coordinates = artifactId.groupId() + "/" + artifactId.artifactId();

			if (coordinates.startsWith("@")) {
				return coordinates.substring(1);
			}

			return coordinates;
		}

	},

	/**
	 * GitHub Actions referenced from workflow files.
	 */
	GITHUB {

		@Override
		public String getCoordinates(ArtifactId artifactId) {
			return artifactId.groupId() + "/" + artifactId.artifactId();
		}

	},

	/**
	 * Other ecosystem.
	 */
	OTHER;

	/**
	 * Return the package name component of the given artifact coordinates.
	 * @param artifactId the artifact coordinates.
	 * @return the package name component.
	 */
	public String getArtifactId(ArtifactId artifactId) {
		return artifactId.artifactId();
	}

	/**
	 * Return the artifact coordinates.
	 * @param artifactId the artifact coordinates.
	 * @return the artifact coordinates.
	 */
	public String getCoordinates(ArtifactId artifactId) {
		return artifactId.toString();
	}

}
