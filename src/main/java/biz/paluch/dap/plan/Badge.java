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

package biz.paluch.dap.plan;

import java.awt.Color;

import com.intellij.ui.JBColor;
import org.jspecify.annotations.Nullable;

/**
 * View model for a badge pill shown in the Upgrade Plan tree such as the
 * upgrade strategy (patch, minor, major, CVE fix) and the ticket reference.
 *
 * @author Mark Paluch
 */
class Badge {

	private static final Color BLUE_FOREGROUND = JBColor.namedColor("Badge.blueForeground",
			new JBColor(0xFFFFFF, 0x212326));

	private static final Color BLUE_BACKGROUND = JBColor.namedColor("Badge.blueBackground",
			new JBColor(0x3871E1, 0x538AF9));

	private static final Color BLUE_SECONDARY_FOREGROUND = JBColor.namedColor("Badge.blueSecondaryForeground",
			new JBColor(0x2F5EB9, 0xD0DFFE));

	private static final Color BLUE_SECONDARY_BACKGROUND = JBColor.namedColor("Badge.blueSecondaryBackground",
			new JBColor(new Color(0x293871E1, true), new Color(0xCC2E4D89, true)));

	private static final Color GREEN_FOREGROUND = JBColor.namedColor("Badge.greenForeground",
			new JBColor(0xFFFFFF, 0x212326));

	private static final Color GREEN_BACKGROUND = JBColor.namedColor("Badge.greenBackground",
			new JBColor(0x338555, 0x4E9D6C));

	private static final Color GREEN_SECONDARY_FOREGROUND = JBColor.namedColor("Badge.greenSecondaryForeground",
			new JBColor(0x2A6E47, 0xCDE5D1));

	private static final Color GREEN_SECONDARY_BACKGROUND = JBColor.namedColor("Badge.greenSecondaryBackground",
			new JBColor(new Color(0x29338555, true), new Color(0xCC29583C, true)));

	private static final Color PURPLE_SECONDARY_FOREGROUND = JBColor.namedColor("Badge.purpleSecondaryForeground",
			new JBColor(0x6C4EBB, 0xE2DBFC));

	private static final Color PURPLE_SECONDARY_BACKGROUND = JBColor.namedColor("Badge.purpleSecondaryBackground",
			new JBColor(new Color(0x298060DB, true), new Color(0xCC574092, true)));

	private static final Color GRAY_SECONDARY_FOREGROUND = JBColor.namedColor("Badge.graySecondaryForeground",
			new JBColor(0x73767C, 0xB5B7BD));

	private static final Color GRAY_SECONDARY_BACKGROUND = JBColor.namedColor("Badge.graySecondaryBackground",
			new JBColor(new Color(0x1F73767C, true), new Color(0x33B5B7BD, true)));

	private static final Color AMBER_SECONDARY_FOREGROUND = JBColor.namedColor(
			"DependencyAssistant.Badge.amberSecondaryForeground", new JBColor(0xA46704, 0xF2C55C));

	private static final Color AMBER_SECONDARY_BACKGROUND = JBColor.namedColor(
			"DependencyAssistant.Badge.amberSecondaryBackground",
			new JBColor(new Color(0x29FFAF0F, true), new Color(0xCC5E4D33, true)));

	enum ColorType {

		BLUE(BLUE_FOREGROUND, BLUE_BACKGROUND),

		BLUE_SECONDARY(BLUE_SECONDARY_FOREGROUND, BLUE_SECONDARY_BACKGROUND),

		GREEN(GREEN_FOREGROUND, GREEN_BACKGROUND),

		GREEN_SECONDARY(GREEN_SECONDARY_FOREGROUND, GREEN_SECONDARY_BACKGROUND),

		PURPLE_SECONDARY(PURPLE_SECONDARY_FOREGROUND, PURPLE_SECONDARY_BACKGROUND),

		GRAY_SECONDARY(GRAY_SECONDARY_FOREGROUND, GRAY_SECONDARY_BACKGROUND),

		AMBER_SECONDARY(AMBER_SECONDARY_FOREGROUND, AMBER_SECONDARY_BACKGROUND);

		private final Color foreground;

		private final Color background;

		ColorType(Color foreground, Color background) {

			this.foreground = foreground;
			this.background = background;
		}

		Color foreground() {
			return foreground;
		}

		Color background() {
			return background;
		}

	}

	private final String text;

	private final ColorType colorType;

	private final @Nullable String tooltip;

	Badge(String text, ColorType colorType, @Nullable String tooltip) {

		this.text = text;
		this.colorType = colorType;
		this.tooltip = tooltip;
	}

	@Nullable
	String tooltip() {
		return tooltip;
	}

	String text() {
		return text;
	}

	ColorType colorType() {
		return colorType;
	}

}
