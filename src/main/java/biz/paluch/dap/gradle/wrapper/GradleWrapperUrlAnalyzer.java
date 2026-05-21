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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.UnknownArtifact;

/**
 * Pure classifier for Gradle wrapper distribution URLs.
 *
 * @author Mark Paluch
 */
class GradleWrapperUrlAnalyzer {

	private GradleWrapperUrlAnalyzer() {
	}

	static List<GradleWrapperUrlProblem> analyze(String decodedValue, String rawText) {

		if (containsCompletionPlaceholder(decodedValue, rawText)) {
			return new ArrayList<>();
		}

		List<GradleWrapperUrlProblem> problems = new ArrayList<>();

		if (GradleWrapperUrlRewriter.containsCredentials(decodedValue)) {
			problems.add(new CredentialsInUrl());
		}

		URI uri;
		try {
			uri = URI.create(decodedValue);
		} catch (IllegalArgumentException ex) {
			problems.add(new InvalidUrl());
			return problems;
		}

		if (uri.getScheme() == null || uri.getHost() == null) {
			problems.add(new InvalidUrl());
			return problems;
		}

		String fileName = GradleWrapperUrlRewriter.lastUrlSegment(decodedValue);
		Matcher matcher = GradleWrapperUtils.GRADLE_DISTRIBUTION_PATTERN.matcher(decodedValue);
		if (matcher.find()) {
			return problems;
		}

		Matcher archiveMatcher = GradleWrapperUrlRewriter.ARCHIVE_PATTERN.matcher(fileName);
		if (archiveMatcher.matches() && !"gradle".equals(archiveMatcher.group("artifact"))) {
			problems.add(new UnknownArtifact(archiveMatcher.group("artifact")));
			return problems;
		}

		problems.add(new MalformedFileName(fileName));
		return problems;
	}

	static boolean isChecksumCandidate(String decodedValue, String rawText) {

		if (containsCompletionPlaceholder(decodedValue, rawText)) {
			return false;
		}

		try {
			String scheme = URI.create(decodedValue).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static boolean containsCompletionPlaceholder(String decodedValue, String rawText) {
		return decodedValue.contains(GradleWrapperUtils.COMPLETION_PLACEHOLDER)
				|| rawText.contains(GradleWrapperUtils.COMPLETION_PLACEHOLDER);
	}

}
