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

package biz.paluch.dap.rule;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.openapi.util.Predicates;

/**
 * Artifact coordinate pattern using {@code *} wildcards.
 *
 * @author Mark Paluch
 */
public class ArtifactPattern implements Predicate<ArtifactId>, Comparable<ArtifactPattern> {

	private final String value;

	private final Predicate<String> groupIdPredicate;

	private final Predicate<String> artifactIdPredicate;

	private final int comparisonValue;

	private ArtifactPattern(String value) {

		this.value = value;
		int separator = separatorIndex(value);
		if (separator == -1) {
			this.groupIdPredicate = Predicates.alwaysTrue();
			this.artifactIdPredicate = glob(value);
		} else {
			this.groupIdPredicate = glob(value.substring(0, separator));
			this.artifactIdPredicate = glob(value.substring(separator + 1));
		}
		this.comparisonValue = determineComparisonValue(value, separator);
	}

	/**
	 * Create an artifact pattern.
	 * @param value the pattern source.
	 */
	public static ArtifactPattern of(String value) {
		return new ArtifactPattern(value);
	}

	@Override
	public boolean test(ArtifactId artifactId) {
		return this.groupIdPredicate.test(artifactId.groupId())
				&& this.artifactIdPredicate.test(artifactId.artifactId());
	}

	int comparisonValue() {
		return this.comparisonValue;
	}

	@Override
	public int compareTo(ArtifactPattern o) {
		return Integer.compare(this.comparisonValue, o.comparisonValue);
	}

	public String value() {
		return this.value;
	}

	@Override
	public String toString() {
		return this.value;
	}

	private static int separatorIndex(String value) {

		int colon = value.indexOf(':');
		int slash = value.indexOf('/');
		if (colon == -1) {
			return slash;
		}
		if (slash == -1) {
			return colon;
		}
		return Math.min(colon, slash);
	}

	private static int determineComparisonValue(String value, int separator) {

		if ("*".equals(value) || "*:*".equals(value) || "*/*".equals(value)) {
			return 0;
		}
		if (separator == -1) {
			return value.contains("*") ? 2 : 3;
		}
		String groupPattern = value.substring(0, separator);
		String artifactPattern = value.substring(separator + 1);
		if (!groupPattern.contains("*") && !artifactPattern.contains("*")) {
			return 4;
		}
		return 1;
	}

	private static Predicate<String> glob(String pattern) {

		Pattern compiled = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
		return compiled.asMatchPredicate();
	}

}
