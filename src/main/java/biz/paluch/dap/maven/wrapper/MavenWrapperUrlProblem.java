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

package biz.paluch.dap.maven.wrapper;

import java.util.List;

import biz.paluch.dap.support.MessageBundle;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.modcommand.PsiUpdateModCommandAction;

/**
 * A specific way a {@code WrapperProperty} URL is malformed or unsafe.
 * <p>Each variant captures only the user-visible deviation payload required to
 * render the inspection message; canonical and suggested values are derived
 * from the {@link WrapperProperty} kind at fix-construction time.
 *
 * @author Mark Paluch
 */
sealed interface MavenWrapperUrlProblem {

	/**
	 * @return the localized inspection message for this problem.
	 */
	String getMessage();

	/**
	 * @param kind the wrapper URL property kind being inspected.
	 * @return the specific quick-fixes offered for this problem, excluding the
	 * generic "use default URL" fallback added by the inspection.
	 */
	List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind);

	/**
	 * Plaintext credentials are embedded in the URL authority.
	 */
	record CredentialsInUrl() implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.credentials-in-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.stripCredentials());
		}

	}

	/**
	 * The URL does not match the canonical Maven coordinate shape and cannot be
	 * classified further.
	 */
	record InvalidUrl() implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.invalid-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of();
		}

	}

	/**
	 * The path version segment disagrees with the file version segment.
	 * @param pathVersion the version that appears as a path segment.
	 * @param fileVersion the version embedded inside the file name.
	 */
	record InconsistentVersion(String pathVersion, String fileVersion) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.inconsistent-version.problem", pathVersion,
					fileVersion);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(
					MavenWrapperUrlFixes.replaceVersion(pathVersion),
					MavenWrapperUrlFixes.replaceVersion(fileVersion));
		}

	}

	/**
	 * The path artifact token disagrees with the file artifact token.
	 * @param pathArtifact the artifactId that appears as a path segment.
	 * @param fileArtifact the artifactId embedded inside the file name.
	 */
	record InconsistentArtifact(String pathArtifact, String fileArtifact) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.inconsistent-artifact.problem", pathArtifact,
					fileArtifact);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceArtifact(kind));
		}

	}

	/**
	 * The last-N segments of the captured groupId do not equal the canonical
	 * group-path for the property kind.
	 * @param actualGroupPath the last-N segments joined by {@code /}.
	 */
	record ImproperGroupId(String actualGroupPath) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.improper-group-id.problem", actualGroupPath);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceGroupPath(kind));
		}

	}

	/**
	 * The artifactId does not equal the canonical artifactId for the property kind.
	 * @param actualArtifactId the artifactId observed in the URL.
	 */
	record UnknownArtifact(String actualArtifactId) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.unknown-artifact.problem", actualArtifactId);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceArtifact(kind));
		}

	}

	/**
	 * The file-name segment does not follow the canonical pattern for the property
	 * kind.
	 * @param actualFileName the file-name segment observed in the URL.
	 * @param sharedVersion the version shared by both URL segments, used by the
	 * file-name fix; never {@literal null} and never empty.
	 */
	record MalformedFileName(String actualFileName, String sharedVersion) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.maven-wrapper.malformed-file-name.problem", actualFileName);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of(MavenWrapperUrlFixes.replaceFileName(kind, sharedVersion));
		}

	}

	/**
	 * The URL is valid but the sibling SHA-256 checksum property is absent.
	 * @param property the URL property whose checksum is missing.
	 */
	record MissingChecksum(WrapperProperty property) implements MavenWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("wrapper.checksum.missing.problem", property.key());
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(WrapperProperty kind) {
			return List.of();
		}

	}

}
