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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.ImproperGroupId;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentArtifact;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentVersion;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.UnknownArtifact;

/**
 * Pure (PSI-free) classifier that maps a wrapper property value to a list of
 * {@link MavenWrapperUrlProblem} variants.
 * <p>The analyzer is text-only: it performs credential and scheme detection by
 * substring scanning (no {@code URI.create}) and coordinate classification via
 * {@link MavenWrapperUtils#MAVEN_ARTIFACT_PATTERN}. Whole-value classification
 * is skipped when the IntelliJ completion placeholder or a {@code ${}}
 * interpolation token is present outside the URL authority; credentials and
 * scheme checks always run.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlAnalyzer {

	private static final String SCHEME_SEPARATOR = "://";

	private static final String INTERPOLATION_TOKEN = "${";

	/**
	 * Classify the given decoded wrapper URL value.
	 * @param property the wrapper property providing canonical coordinates; must
	 * not be {@literal null}.
	 * @param decodedValue the Java-properties-unescaped property value; must not be
	 * {@literal null}.
	 * @param rawText the raw property text used for completion-placeholder
	 * detection; must not be {@literal null}.
	 * @return an immutable list of detected problems; empty when the value
	 * classifies cleanly or is skipped.
	 */
	static List<MavenWrapperUrlProblem> analyze(WrapperProperty property, String decodedValue, String rawText) {

		List<MavenWrapperUrlProblem> problems = new ArrayList<>();

		if (containsCredentials(decodedValue)) {
			problems.add(new CredentialsInUrl());
		}

		if (shouldSkipWholeValueClassification(decodedValue, rawText)) {
			return List.copyOf(problems);
		}

		classifyCoordinates(property, decodedValue, problems);
		return List.copyOf(problems);
	}

	private static void classifyCoordinates(WrapperProperty property, String decodedValue,
			List<MavenWrapperUrlProblem> problems) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(decodedValue);
		if (!matcher.find()) {
			problems.add(new InvalidUrl());
			return;
		}

		String groupId = matcher.group("groupId");
		String pathArtifact = matcher.group("artifactId1");
		String pathVersion = matcher.group("version1");
		String fileArtifact = matcher.group("artifactId2");
		String fileVersion = matcher.group("version2");

		String canonicalGroupPath = property.canonicalGroupPath();
		String canonicalArtifactId = property.canonicalArtifactId();

		boolean versionInconsistent = !pathVersion.isEmpty() && !fileVersion.isEmpty()
				&& !pathVersion.equals(fileVersion);
		if (versionInconsistent) {
			problems.add(new InconsistentVersion(pathVersion, fileVersion));
		}

		boolean pathArtifactCanonical = pathArtifact.equals(canonicalArtifactId);
		boolean fileArtifactCanonical = fileArtifact.equals(canonicalArtifactId);
		if (!pathArtifactCanonical || !fileArtifactCanonical) {
			if (!pathArtifact.equals(fileArtifact)) {
				problems.add(new InconsistentArtifact(pathArtifact, fileArtifact));
			}
			problems.add(new UnknownArtifact(fileArtifactCanonical ? pathArtifact : fileArtifact));
		}

		String[] canonicalSegments = canonicalGroupPath.split("/");
		String actualGroupTail = lastSegments(groupId, canonicalSegments.length);
		if (!actualGroupTail.equals(canonicalGroupPath)) {
			problems.add(new ImproperGroupId(actualGroupTail));
		}

		if (!versionInconsistent) {
			String sharedVersion = pathVersion.isEmpty() ? fileVersion : pathVersion;
			String actualFileName = lastUrlSegment(decodedValue);
			if (!sharedVersion.isEmpty() && !property.isCanonicalFileName(actualFileName, sharedVersion)) {
				problems.add(new MalformedFileName(actualFileName, sharedVersion));
			}
		}
	}

	/**
	 * Return whether the URL's authority substring contains a literal {@code @}
	 * character indicating embedded credentials.
	 * @param decodedValue the decoded property value; must not be {@literal null}.
	 * @return {@literal true} if credentials are detected; {@literal false}
	 * otherwise.
	 */
	static boolean containsCredentials(String decodedValue) {

		int authorityStart = authorityStart(decodedValue);
		if (authorityStart < 0) {
			return false;
		}
		int authorityEnd = authorityEnd(decodedValue, authorityStart);
		int at = decodedValue.indexOf('@', authorityStart);
		return at >= 0 && at < authorityEnd;
	}

	/**
	 * Return the start offset of the authority segment (the index just after
	 * {@code ://}), or {@literal -1} when the input does not contain a scheme
	 * separator.
	 * @param url the URL to inspect; must not be {@literal null}.
	 * @return the authority start offset, or {@literal -1}.
	 */
	static int authorityStart(String url) {

		int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
		if (schemeEnd < 0) {
			return -1;
		}
		return schemeEnd + SCHEME_SEPARATOR.length();
	}

	/**
	 * Return the end offset of the authority segment (the index of the first
	 * {@code /} at or after {@code authorityStart}, or the string length when no
	 * path separator follows).
	 * @param url the URL to inspect; must not be {@literal null}.
	 * @param authorityStart the offset returned by {@link #authorityStart(String)};
	 * must be non-negative.
	 * @return the authority end offset.
	 */
	static int authorityEnd(String url, int authorityStart) {

		int end = url.indexOf('/', authorityStart);
		return end < 0 ? url.length() : end;
	}

	private static boolean shouldSkipWholeValueClassification(String decodedValue, String rawText) {

		if (decodedValue.contains(MavenWrapperUtils.COMPLETION_PLACEHOLDER)
				|| rawText.contains(MavenWrapperUtils.COMPLETION_PLACEHOLDER)) {
			return true;
		}
		return hasInterpolationOutsideAuthority(decodedValue);
	}

	private static boolean hasInterpolationOutsideAuthority(String decodedValue) {

		int firstToken = decodedValue.indexOf(INTERPOLATION_TOKEN);
		if (firstToken < 0) {
			return false;
		}

		int authorityStart = authorityStart(decodedValue);
		if (authorityStart < 0) {
			return true;
		}

		int authorityEnd = authorityEnd(decodedValue, authorityStart);

		for (int token = firstToken; token >= 0; token = decodedValue.indexOf(INTERPOLATION_TOKEN,
				token + INTERPOLATION_TOKEN.length())) {
			if (token < authorityStart || token >= authorityEnd) {
				return true;
			}
		}
		return false;
	}

	private static String lastSegments(String groupPath, int count) {

		String[] segments = groupPath.split("/");
		if (segments.length <= count) {
			return groupPath;
		}
		return String.join("/", Arrays.asList(segments).subList(segments.length - count, segments.length));
	}

	private static String lastUrlSegment(String url) {

		int lastSlash = url.lastIndexOf('/');
		return lastSlash < 0 ? url : url.substring(lastSlash + 1);
	}

	private MavenWrapperUrlAnalyzer() {
	}

}
