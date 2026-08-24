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
 * Normalized qualitative severity of a vulnerability.
 *
 * <p>Numeric ratings use the CVSS v3.1 qualitative severity bands.
 * {@link #UNKNOWN} represents scores or labels that cannot be mapped to a known
 * band rather than assigning them a severity by default.
 *
 * @author Mark Paluch
 */
public enum CvssSeverity {

	CRITICAL, HIGH, MEDIUM, LOW, NONE, UNKNOWN;

	/**
	 * Map a numeric CVSS base score to its qualitative severity band.
	 *
	 * <p>Scores at or above {@code 9.0} are critical, {@code 7.0} through
	 * {@code 8.9} are high, {@code 4.0} through {@code 6.9} are medium, {@code 0.1}
	 * through {@code 3.9} are low, and exactly {@code 0.0} is {@link #NONE}.
	 * Negative values and {@link Double#NaN} yield {@link #UNKNOWN}.
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
	 *
	 * <p>Matching is case-insensitive against the enum constant names. Any label
	 * that does not match a known constant, including {@literal null} or a blank
	 * string, yields {@link #UNKNOWN}.
	 *
	 * @param label the severity label reported by the source, or {@literal null} if
	 * absent.
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

	/**
	 * Return the localized label for this severity.
	 *
	 * @return the localized severity label.
	 */
	public String getLabel() {
		return MessageBundle.message("CvssSeverity." + name());
	}

	/**
	 * Return the ordering rank for severity comparisons.
	 *
	 * <p>Higher values represent more severe ratings independently of enum
	 * declaration order.
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
