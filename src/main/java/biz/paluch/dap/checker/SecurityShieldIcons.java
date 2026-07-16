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

import biz.paluch.dap.util.ResolvableIcon;

/**
 * Single source for the severity-to-shield mapping shared by every
 * vulnerability surface.
 *
 * @author Mark Paluch
 * @see CheckerIcons
 * @see CvssSeverity
 */
public enum SecurityShieldIcons {

	/**
	 * The filled shield, for emphasis surfaces.
	 */
	FILLED {

		@Override
		public ResolvableIcon resolve(CvssSeverity severity) {

			return switch (severity) {
			case CRITICAL, HIGH -> HIGH_FILLED;
			case MEDIUM -> MEDIUM_FILLED;
			case LOW -> LOW_FILLED;
			case NONE, UNKNOWN -> UNKNOWN_FILLED;
			};
		}

	},

	/**
	 * The outline shield, for inline surfaces.
	 */
	OUTLINE {

		@Override
		public ResolvableIcon resolve(CvssSeverity severity) {
			return switch (severity) {
			case CRITICAL, HIGH -> HIGH_OUTLINE;
			case MEDIUM -> MEDIUM_OUTLINE;
			case LOW -> LOW_OUTLINE;
			case NONE, UNKNOWN -> UNKNOWN_OUTLINE;
			};
		}

	};


	private static final ResolvableIcon HIGH_FILLED = new ResolvableIcon(CheckerIcons.HIGH,
			"biz.paluch.dap.checker.CheckerIcons.HIGH");

	private static final ResolvableIcon MEDIUM_FILLED = new ResolvableIcon(CheckerIcons.MEDIUM,
			"biz.paluch.dap.checker.CheckerIcons.MEDIUM");

	private static final ResolvableIcon LOW_FILLED = new ResolvableIcon(CheckerIcons.LOW,
			"biz.paluch.dap.checker.CheckerIcons.LOW");

	private static final ResolvableIcon UNKNOWN_FILLED = new ResolvableIcon(CheckerIcons.UNKNOWN,
			"biz.paluch.dap.checker.CheckerIcons.UNKNOWN");

	private static final ResolvableIcon HIGH_OUTLINE = new ResolvableIcon(CheckerIcons.HIGH_OUTLINE,
			"biz.paluch.dap.checker.CheckerIcons.HIGH");

	private static final ResolvableIcon MEDIUM_OUTLINE = new ResolvableIcon(CheckerIcons.MEDIUM_OUTLINE,
			"biz.paluch.dap.checker.CheckerIcons.MEDIUM");

	private static final ResolvableIcon LOW_OUTLINE = new ResolvableIcon(CheckerIcons.LOW_OUTLINE,
			"biz.paluch.dap.checker.CheckerIcons.LOW");

	private static final ResolvableIcon UNKNOWN_OUTLINE = new ResolvableIcon(CheckerIcons.UNKNOWN_OUTLINE,
			"biz.paluch.dap.checker.CheckerIcons.UNKNOWN");

	/**
	 * Return the shield icon for the given severity in this style.
	 *
	 * @param severity the highest advisory severity.
	 * @return the resolvable shield icon; never {@literal null}.
	 */
	public abstract ResolvableIcon resolve(CvssSeverity severity);

}
