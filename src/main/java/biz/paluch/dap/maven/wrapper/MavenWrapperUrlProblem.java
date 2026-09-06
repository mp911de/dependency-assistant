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

package biz.paluch.dap.maven.wrapper;

import java.util.List;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.PsiUpdateModCommandAction;

/**
 * Maven Wrapper URL problem with a message and applicable repairs.
 *
 * @author Mark Paluch
 */
sealed interface MavenWrapperUrlProblem {

	String getMessage();

	/**
	 * Return problem-specific fixes. The inspection adds the default-URL fallback.
	 */
	List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind);

	record CredentialsInUrl() implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.credentials-in-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.stripCredentials());
		}

	}

	/**
	 * The URL does not have a recognizable Maven coordinate shape.
	 */
	record InvalidUrl() implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.invalid-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of();
		}

	}

	/**
	 * The path and filename versions differ.
	 */
	record InconsistentVersion(String pathVersion, String fileVersion) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.inconsistent-version.problem", pathVersion,
					fileVersion);
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(
					MavenWrapperUrlFixes.replaceVersion(pathVersion),
					MavenWrapperUrlFixes.replaceVersion(fileVersion));
		}

	}

	/**
	 * The path and filename artifact IDs differ.
	 */
	record InconsistentArtifact(String pathArtifact, String fileArtifact) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.inconsistent-artifact.problem", pathArtifact,
					fileArtifact);
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceArtifact(kind));
		}

	}

	/**
	 * The group path differs from the wrapper property's coordinates.
	 */
	record ImproperGroupId(String actualGroupPath) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.improper-group-id.problem", actualGroupPath);
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceGroupPath(kind));
		}

	}

	/**
	 * The artifact ID is not the artifact expected for this property.
	 */
	record UnknownArtifact(String actualArtifactId) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.unknown-artifact.problem", actualArtifactId);
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceArtifact(kind));
		}

	}

	/**
	 * The filename does not match the wrapper artifact.
	 * @param sharedVersion the version agreed by the path and filename.
	 */
	record MalformedFileName(String actualFileName, String sharedVersion) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.malformed-file-name.problem", actualFileName);
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceFileName(kind, sharedVersion));
		}

	}

	record MissingChecksum(WrapperProperty property) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("wrapper.checksum.missing.problem", property.key());
		}

		@Override
		public List<PsiUpdateModCommandAction<Property>> getFixes(WrapperProperty kind) {
			return List.of();
		}

	}

}
