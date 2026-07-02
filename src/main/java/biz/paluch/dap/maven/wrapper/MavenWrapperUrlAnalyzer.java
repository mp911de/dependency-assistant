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

import java.net.URI;
import java.util.ArrayList;
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
 * <p>The analyzer is text-only: credential detection runs through
 * {@link URI#getUserInfo()} (the same parser used by
 * {@link WrapperProperty#parseProperty}) and coordinate classification uses
 * {@link MavenWrapperUtils#MAVEN_ARTIFACT_PATTERN}. Whole-value classification
 * is skipped when the IntelliJ completion placeholder or a {@code ${}}
 * interpolation token is present outside the URL authority; credentials and
 * scheme checks always run.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlAnalyzer {

	/**
	 * Classify the given decoded wrapper URL value.
	 * @param property the wrapper property providing canonical coordinates; must
	 * not be {@literal null}.
	 * @param decodedValue the Java-properties-unescaped property value; must not be
	 * {@literal null}.
	 * @param rawText the raw property text used for completion-placeholder
	 * detection.
	 * @return an immutable list of detected problems; empty when the value
	 * classifies cleanly or is skipped.
	 */
	static List<MavenWrapperUrlProblem> analyze(WrapperProperty property, String decodedValue, String rawText) {

		List<MavenWrapperUrlProblem> problems = new ArrayList<>();

		if (containsCredentials(decodedValue)) {
			problems.add(new CredentialsInUrl());
		}

		if (shouldSkipWholeValueClassification(decodedValue, rawText)) {
			return problems;
		}

		classifyCoordinates(property, decodedValue, problems);
		return problems;
	}

	static boolean isChecksumCandidate(String decodedValue, String rawText) {

		if (shouldSkipWholeValueClassification(decodedValue, rawText)) {
			return false;
		}

		try {
			String scheme = URI.create(decodedValue).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException ex) {
			return false;
		}
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
			} else {
				problems.add(new UnknownArtifact(pathArtifact));
			}
		}

		String[] canonicalSegments = canonicalGroupPath.split("/");
		String actualGroupTail = MavenWrapperUrlRewriter.lastSegments(groupId, canonicalSegments.length);
		if (!actualGroupTail.equals(canonicalGroupPath)) {
			problems.add(new ImproperGroupId(actualGroupTail));
		}

		if (!versionInconsistent) {
			String sharedVersion = pathVersion.isEmpty() ? fileVersion : pathVersion;
			String actualFileName = MavenWrapperUrlRewriter.lastUrlSegment(decodedValue);
			if (!sharedVersion.isEmpty() && !property.isCanonicalFileName(actualFileName, sharedVersion)) {
				problems.add(new MalformedFileName(actualFileName, sharedVersion));
			}
		}
	}

	/**
	 * Return whether the URL embeds {@code user[:password]@} credentials.
	 * <p>Credential detection delegates to {@link URI#getUserInfo()} so the
	 * analyzer flags the same set of inputs that
	 * {@link WrapperProperty#parseProperty} would parse as credentials, including
	 * opaque and scheme-less URIs.
	 * @param decodedValue the decoded property value.
	 * @return {@literal true} if credentials are detected; {@literal false}
	 * otherwise.
	 */
	static boolean containsCredentials(String decodedValue) {

		try {
			return URI.create(decodedValue).getUserInfo() != null;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static boolean shouldSkipWholeValueClassification(String decodedValue, String rawText) {

		if (decodedValue.contains(MavenWrapperUtils.COMPLETION_PLACEHOLDER)
				|| rawText.contains(MavenWrapperUtils.COMPLETION_PLACEHOLDER)) {
			return true;
		}
		return hasInterpolationOutsideAuthority(decodedValue);
	}

	private static boolean hasInterpolationOutsideAuthority(String decodedValue) {

		int firstToken = decodedValue.indexOf(MavenWrapperUrlRewriter.INTERPOLATION_TOKEN);
		if (firstToken < 0) {
			return false;
		}

		int authorityStart = MavenWrapperUrlRewriter.authorityStart(decodedValue);
		if (authorityStart < 0) {
			return true;
		}

		int authorityEnd = MavenWrapperUrlRewriter.authorityEnd(decodedValue, authorityStart);

		for (int token = firstToken; token >= 0; token = decodedValue.indexOf(
				MavenWrapperUrlRewriter.INTERPOLATION_TOKEN,
				token + MavenWrapperUrlRewriter.INTERPOLATION_TOKEN.length())) {
			if (token < authorityStart || token >= authorityEnd) {
				return true;
			}
		}
		return false;
	}

	private MavenWrapperUrlAnalyzer() {
	}

}
