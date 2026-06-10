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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MatchFunction;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Package-local utilities for Gradle wrapper property files.
 *
 * @author Mark Paluch
 */
class GradleWrapperUtils {

	public static final String COMPLETION_PLACEHOLDER = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

	public static final Pattern GRADLE_DISTRIBUTION_PATTERN = Pattern.compile(
			"(?<fileName>gradle-(?<version>(?:\\d[\\w.-]*?)?(" + Pattern.quote(COMPLETION_PLACEHOLDER)
					+ ")?(?:\\d[\\w.-]*?)?)-(?<flavor>bin|all)\\.zip)(?=$|[?#])");

	private static final int MAX_MATCH_LENGTH = 2048;

	public static final String WRAPPER_FILENAME = "gradle-wrapper.properties";

	private GradleWrapperUtils() {
	}

	static @Nullable String findSha(ArtifactId artifactId, ArtifactVersion version, StateService stateService) {

		Releases releases = stateService.getCache()
				.getReleases(artifactId);

		for (Release release : releases) {
			if (release.getVersion().getVersion()
					.equals(version.getVersion().getVersion())) {
				if (release.getVersion() instanceof GitVersion gitVersion && gitVersion.hasSha()) {
					return gitVersion.getRequiredSha();
				}
			}
		}

		return null;
	}

	/**
	 * Return file-absolute ranges for the version segment in a Gradle wrapper URL.
	 */
	static List<TextRange> getVersionRanges(PropertyImpl property) {

		String value = property.getUnescapedValue();
		if (value == null || value.length() > MAX_MATCH_LENGTH) {
			return List.of();
		}

		String stripped = value.replace(COMPLETION_PLACEHOLDER, "");
		Matcher matcher = GRADLE_DISTRIBUTION_PATTERN.matcher(stripped);
		if (!matcher.find()) {
			return List.of();
		}

		int versionStart = expandStrippedPosition(matcher.start("version"), value);
		int versionEnd = expandStrippedPosition(matcher.end("version"), value);

		return PropertyUtils.findTextRanges(property, (str, index) -> {
			if (index <= versionStart) {
				return MatchFunction.match(value.substring(versionStart, versionEnd), versionStart, versionEnd);
			}
			return MatchFunction.noMatch();
		});
	}

	private static int expandStrippedPosition(int strippedPos, String original) {

		int placeholderLength = COMPLETION_PLACEHOLDER.length();
		int pos = strippedPos;
		int from = 0;
		while (from < pos) {
			int hit = original.indexOf(COMPLETION_PLACEHOLDER, from);
			if (hit < 0 || hit >= pos) {
				break;
			}
			pos += placeholderLength;
			from = hit + placeholderLength;
		}
		return pos;
	}

	static boolean isWrapperFile(@Nullable VirtualFile file) {
		return file != null && WRAPPER_FILENAME.equals(file.getName());
	}

	static boolean isWrapperFile(@Nullable PsiFile file) {
		return file instanceof PropertiesFile && WRAPPER_FILENAME.equals(file.getName());
	}

	static boolean isWrapperFile(PropertiesFile file) {
		return WRAPPER_FILENAME.equals(file.getName());
	}

	static ProjectId createProjectId(VirtualFile virtualFile) {
		return new ProjectId("org.gradle", "gradle", virtualFile.getPath());
	}

}
