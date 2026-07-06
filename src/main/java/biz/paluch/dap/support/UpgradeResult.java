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

package biz.paluch.dap.support;

/**
 * Result of applying one or more dependency updates, expressed as the number of
 * update steps that changed their target build files.
 *
 * @author Mark Paluch
 */
public class UpgradeResult {

	private static final UpgradeResult NONE = new UpgradeResult(0);

	private static final UpgradeResult CHANGED = new UpgradeResult(1);

	private final int changeCount;

	private UpgradeResult(int changeCount) {
		if (changeCount < 0) {
			throw new IllegalArgumentException("Change count must not be negative");
		}
		this.changeCount = changeCount;
	}

	/**
	 * Return a result representing no change.
	 */
	public static UpgradeResult none() {
		return NONE;
	}

	/**
	 * Return a result representing one changed update step.
	 */
	public static UpgradeResult changed() {
		return CHANGED;
	}

	/**
	 * Return a result with the given number of changed update steps.
	 *
	 * @param changeCount the non-negative change count.
	 */
	public static UpgradeResult of(int changeCount) {
		return switch (changeCount) {
		case 0 -> NONE;
		case 1 -> CHANGED;
		default -> new UpgradeResult(changeCount);
		};
	}

	/**
	 * Combine this result with another apply result.
	 *
	 * @param other the result to combine with this one.
	 * @return the combined result.
	 */
	public UpgradeResult merge(UpgradeResult other) {
		return other.changeCount == 0 ? this : of(Math.addExact(changeCount, other.changeCount));
	}

	/**
	 * Return the number of changed update steps.
	 */
	public int getChangeCount() {
		return changeCount;
	}

	/**
	 * Return whether any update step changed a build file.
	 */
	public boolean hasChanges() {
		return changeCount > 0;
	}

	@Override
	public String toString() {
		return "UpgradeResult[changeCount=" + changeCount + "]";
	}

}
