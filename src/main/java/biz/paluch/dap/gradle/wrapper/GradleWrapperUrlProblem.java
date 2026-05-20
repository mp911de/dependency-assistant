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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;

import biz.paluch.dap.support.MessageBundle;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.modcommand.PsiUpdateModCommandAction;

/**
 * A specific way a Gradle wrapper distribution URL is malformed or unsafe.
 *
 * @author Mark Paluch
 */
sealed interface GradleWrapperUrlProblem {

	/**
	 * @return the localized inspection message for this problem.
	 */
	String getMessage();

	/**
	 * @param version the version to use when rewriting the distribution URL.
	 * @return the specific quick-fixes offered for this problem, excluding the
	 * generic "use default URL" fallback added by the inspection.
	 */
	List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(String version);

	record CredentialsInUrl() implements GradleWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.gradle-wrapper.credentials-in-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(String version) {
			return List.of(new GradleWrapperUrlFixes.WrapperUrlFix(GradleWrapperUrlRewriter::stripCredentials,
					"inspection.gradle-wrapper.credentials-in-url.fix"));
		}

	}

	record InvalidUrl() implements GradleWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.gradle-wrapper.invalid-url.problem");
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(String version) {
			return List.of();
		}

	}

	record UnknownArtifact(String actualArtifactId) implements GradleWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.gradle-wrapper.unknown-artifact.problem", actualArtifactId);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(String version) {
			return List.of(GradleWrapperUrlFixes.replaceFileName(version));
		}

	}

	record MalformedFileName(String actualFileName) implements GradleWrapperUrlProblem {

		@Override
		public String getMessage() {
			return MessageBundle.message("inspection.gradle-wrapper.malformed-file-name.problem", actualFileName);
		}

		@Override
		public List<PsiUpdateModCommandAction<PropertyImpl>> getFixes(String version) {
			return List.of(GradleWrapperUrlFixes.replaceFileName(version));
		}

	}

}
