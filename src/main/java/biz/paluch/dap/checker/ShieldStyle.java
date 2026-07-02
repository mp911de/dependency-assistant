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

package biz.paluch.dap.checker;

import biz.paluch.dap.util.ResolvableIcon;

/**
 * Visual weight of a vulnerability shield, chosen by presentation surface
 * rather than by severity.
 *
 * <p>The same severity renders as a {@link #FILLED} shield on emphasis surfaces
 * (the documentation popup, the dependency dialog, the quick-fix) and as an
 * {@link #OUTLINE} shield on inline surfaces (the completion lookup, the gutter
 * line marker). Only the shield differs by surface; the age, rule, and preview
 * icons are shared.
 *
 * @author Mark Paluch
 */
// TODO: Inline
public enum ShieldStyle {

	/**
	 * The filled shield, for emphasis surfaces.
	 */
	FILLED {

		@Override
		public ResolvableIcon shield(CvssSeverity severity) {
			return SecurityShieldIcons.filled(severity);
		}

	},

	/**
	 * The outline shield, for inline surfaces.
	 */
	OUTLINE {

		@Override
		public ResolvableIcon shield(CvssSeverity severity) {
			return SecurityShieldIcons.outline(severity);
		}

	};

	/**
	 * Return the shield icon for the given severity in this style.
	 *
	 * @param severity the highest advisory severity.
	 * @return the resolvable shield icon; never {@literal null}.
	 */
	public abstract ResolvableIcon shield(CvssSeverity severity);

}
