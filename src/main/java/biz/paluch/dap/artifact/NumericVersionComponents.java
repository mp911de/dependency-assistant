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

package biz.paluch.dap.artifact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import biz.paluch.dap.util.StringUtils;

/**
 * Numeric components of a version.
 *
 * @author Mark Paluch
 */
public class NumericVersionComponents implements Comparable<NumericVersionComponents> {

	private final BigDecimal[] parts;

	/**
	 * Creates a new {@link NumericVersionComponents} from the given integer values.
	 * At least one value has to be given.
	 *
	 * @param parts must not be {@literal null} or empty.
	 */
	private NumericVersionComponents(int... parts) {
		this(Arrays.stream(parts).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new));
	}

	/**
	 * Creates a new {@link NumericVersionComponents} from the given integer values.
	 */
	private NumericVersionComponents(int major, int minor, int bugfix) {
		this(BigDecimal.valueOf(major), BigDecimal.valueOf(minor), BigDecimal.valueOf(bugfix));
	}

	/**
	 * Creates a new {@link NumericVersionComponents} from the given integer values.
	 * At least one value has to be given.
	 *
	 * @param parts must not be {@literal null} or empty.
	 */
	private NumericVersionComponents(BigDecimal... parts) {

		if (parts == null) {
			throw new IllegalArgumentException("Parts must not be null!");
		}
		if (parts.length == 0) {
			throw new IllegalArgumentException("We need at least 1 part!");
		}

		this.parts = parts;

		if (getMajor() < 0) {
			throw new IllegalArgumentException("Major version must be greater or equal zero!");
		}
		if (getMinor() < 0) {
			throw new IllegalArgumentException("Minor version must be greater or equal zero!");
		}
	}

	/**
	 * Create version components from the given parts.
	 * @param parts the numeric version parts.
	 */
	public static NumericVersionComponents of(int... parts) {
		return new NumericVersionComponents(
				Arrays.stream(parts).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new));
	}

	/**
	 * Parse the given string representation of a version into a
	 * {@link NumericVersionComponents} object.
	 * @param version the version string to parse.
	 */
	public static NumericVersionComponents parse(String version) {

		if (StringUtils.isEmpty(version)) {
			throw new IllegalArgumentException("Version must not be null or empty!");
		}

		String[] parts = version.trim().split("\\.");
		BigDecimal[] intParts = new BigDecimal[parts.length];

		for (int i = 0; i < parts.length; i++) {
			intParts[i] = new BigDecimal(parts[i]);
		}

		return new NumericVersionComponents(intParts);
	}

	/**
	 * Return the number of available components.
	 */
	public int getComponents() {
		return parts.length;
	}

	/**
	 * Return the numeric version parts.
	 * @return a new array containing the numeric version parts.
	 */
	public int[] getParts() {
		return Arrays.stream(parts).mapToInt(BigDecimal::intValue).toArray();
	}

	/**
	 * Return the major component.
	 */
	public int getMajor() {
		return parts.length > 0 ? parts[0].intValue() : 0;
	}

	/**
	 * Return the minor component.
	 */
	public int getMinor() {
		return parts.length > 1 ? parts[1].intValue() : 0;
	}

	/**
	 * Return the bugfix component.
	 */
	public int getBugfix() {
		return parts.length > 2 ? parts[2].intValue() : 0;
	}

	/**
	 * Return the build component.
	 */
	public int getBuild() {
		return parts.length > 3 ? parts[3].intValue() : 0;
	}

	/**
	 * Return whether the current {@link NumericVersionComponents} is the same as
	 * the given one.
	 * @param version the version to compare with.
	 */
	public boolean is(NumericVersionComponents version) {
		return equals(version);
	}

	/**
	 * Return whether the current {@link NumericVersionComponents} has the same
	 * major and minor version as the given one.
	 * @param other the version to compare with.
	 */
	public boolean hasSameMajorMinor(NumericVersionComponents other) {
		return getMajor() == other.getMajor() && getMinor() == other.getMinor();
	}

	/**
	 * Return the next minor version line.
	 */
	public NumericVersionComponents nextMinor() {
		return new NumericVersionComponents(getMajor(), getMinor() + 1, 0);
	}

	/**
	 * Return the next bugfix version.
	 */
	public NumericVersionComponents nextBugfix() {
		return new NumericVersionComponents(getMajor(), getMinor(), getBugfix() + 1);
	}

	/**
	 * Return a copy with the given bugfix component.
	 */
	public NumericVersionComponents withBugfix(int bugfix) {
		return new NumericVersionComponents(getMajor(), getMinor(), bugfix);
	}

	/**
	 * Return the {@code major.minor.bugfix} representation.
	 */
	public String toMajorMinorBugfix() {
		return "%s.%s.%s".formatted(getMajor(), getMinor(), getBugfix());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(NumericVersionComponents that) {

		if (that == null) {
			return 1;
		}

		int maxLength = Math.max(this.parts.length, that.parts.length);

		for (int i = 0; i < maxLength; i++) {

			BigDecimal thisPart = i < this.parts.length ? this.parts[i] : BigDecimal.ZERO;
			BigDecimal thatPart = i < that.parts.length ? that.parts[i] : BigDecimal.ZERO;

			int comparison = thisPart.compareTo(thatPart);
			if (comparison != 0) {
				return comparison;
			}
		}

		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof NumericVersionComponents other)) {
			return false;
		}

		return compareTo(other) == 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		int last = parts.length;
		while (last > 0 && parts[last - 1].signum() == 0) {
			last--;
		}
		int result = 1;
		for (int i = 0; i < last; i++) {
			BigDecimal canonical = parts[i].signum() == 0 ? BigDecimal.ZERO : parts[i].stripTrailingZeros();
			result = 31 * result + canonical.hashCode();
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		List<BigDecimal> digits = new ArrayList<>(Arrays.asList(parts));

		while (digits.size() < 1) {
			digits.add(BigDecimal.ZERO);
		}

		return org.springframework.util.StringUtils.collectionToDelimitedString(digits, ".");
	}

}
