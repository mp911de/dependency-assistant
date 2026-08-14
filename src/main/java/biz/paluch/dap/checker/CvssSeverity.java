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

package biz.paluch.dap.checker;

import biz.paluch.dap.util.MessageBundle;
import org.jspecify.annotations.Nullable;

/**
 * Qualitative severity rating of a vulnerability.
 * <p>Ratings follow the CVSS v3.1 qualitative severity bands. {@link #UNKNOWN}
 * covers scores or labels that cannot be mapped to a known band so that an
 * unrecognized input is reported as unknown rather than defaulting to a
 * misleading mid-tier band.
 *
 * @author Mark Paluch
 */
public enum CvssSeverity {

	CRITICAL, HIGH, MEDIUM, LOW, NONE, UNKNOWN;

	/**
	 * Map a numeric CVSS base score to its qualitative severity band.
	 * <p>Follows the CVSS v3.1 bands: {@code >= 9.0} CRITICAL, {@code 7.0 - 8.9}
	 * HIGH, {@code 4.0 - 6.9} MEDIUM, {@code 0.1 - 3.9} LOW, {@code 0.0}
	 * {@link #NONE}. A negative score yields {@link #UNKNOWN}.
	 *
	 * @param score the CVSS base score.
	 * @return the qualitative severity band.
	 */
	public static CvssSeverity fromScore(double score) {

		if (score >= 9.0) {
			return CRITICAL;
		}
		if (score >= 7.0) {
			return HIGH;
		}
		if (score >= 4.0) {
			return MEDIUM;
		}
		if (score >= 0.1) {
			return LOW;
		}
		if (score == 0) {
			return NONE;
		}
		return UNKNOWN;
	}

	/**
	 * Map an explicit severity label to its severity band.
	 * <p>Matching is case-insensitive against the enum constant names. Any label
	 * that does not match a known constant, including {@literal null} or a blank
	 * string, yields {@link #UNKNOWN} rather than a misleading mid-tier band.
	 *
	 * @param label the severity label as reported by the source, can be
	 * {@literal null}.
	 * @return the matching severity band.
	 */
	public static CvssSeverity fromLabel(@Nullable String label) {

		if (label == null) {
			return UNKNOWN;
		}

		for (CvssSeverity severity : values()) {
			if (severity.name().equalsIgnoreCase(label.trim())) {
				return severity;
			}
		}
		return UNKNOWN;
	}

	public String getLabel() {
		return MessageBundle.message("CvssSeverity." + name());
	}

	/**
	 * Return the ordering rank for severity comparisons.
	 * <p>Higher values represent more severe ratings.
	 *
	 * @return the severity rank.
	 */
	public int rank() {
		return switch (this) {
		case CRITICAL -> 5;
		case HIGH -> 4;
		case MEDIUM -> 3;
		case LOW -> 2;
		case NONE -> 1;
		case UNKNOWN -> 0;
		};
	}

}
