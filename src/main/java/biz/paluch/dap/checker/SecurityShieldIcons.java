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
 * <p>The mapping collapses {@link CvssSeverity} bands onto the
 * {@link CheckerIcons} shield set: {@code CRITICAL} and {@code HIGH} share the
 * high shield, {@code MEDIUM} and {@code LOW} keep their own, and every other
 * band (including {@code NONE} and {@code UNKNOWN}) renders the unknown shield.
 * Each band resolves to a filled or an {@code _OUTLINE} icon chosen by the
 * surface: the gutter reads the {@link #outline(CvssSeverity) outline} form,
 * the alt-enter fix the {@link #filled(CvssSeverity) filled} form.
 *
 * <p>Both forms are {@link ResolvableIcon}s so they render either as a Swing
 * icon or, for the documentation popup, as the reflective {@code <icon src>}
 * path. The reflective path always references the plugin-owned
 * {@link CheckerIcons} filled-shield fields rather than a bundled IDE icon set,
 * keeping the shields resolvable in every IDE edition independent of whether
 * JetBrains' package-checker icons are present. The outline form carries that
 * same filled path because the documentation popup renders the filled weight
 * regardless of surface.
 *
 * @author Mark Paluch
 * @see CheckerIcons
 * @see CvssSeverity
 */
public class SecurityShieldIcons {

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

	private SecurityShieldIcons() {
	}

	/**
	 * Return the outline shield for the given severity, for inline surfaces (the
	 * gutter, the completion lookup).
	 *
	 * @param severity the highest advisory severity.
	 * @return the outline shield icon; never {@literal null}.
	 */
	public static ResolvableIcon outline(CvssSeverity severity) {
		return switch (severity) {
		case CRITICAL, HIGH -> HIGH_OUTLINE;
		case MEDIUM -> MEDIUM_OUTLINE;
		case LOW -> LOW_OUTLINE;
		case NONE, UNKNOWN -> UNKNOWN_OUTLINE;
		};
	}

	/**
	 * Return the filled shield for the given severity, for emphasis surfaces (the
	 * documentation popup, the dependency dialog, the quick-fix).
	 *
	 * @param severity the highest advisory severity.
	 * @return the filled shield icon; never {@literal null}.
	 */
	public static ResolvableIcon filled(CvssSeverity severity) {
		return switch (severity) {
		case CRITICAL, HIGH -> HIGH_FILLED;
		case MEDIUM -> MEDIUM_FILLED;
		case LOW -> LOW_FILLED;
		case NONE, UNKNOWN -> UNKNOWN_FILLED;
		};
	}

}
