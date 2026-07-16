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

package biz.paluch.dap.github;

import java.awt.Color;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ui.JBColor;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jspecify.annotations.Nullable;

/**
 * GitHub {@link Label} identified by its name, carrying the API hex color.
 *
 * @author Mark Paluch
 */
class GitHubLabel implements Label {

	private final String name;

	private final String description;

	private final @Nullable String hexColor;

	private final @Nullable JBColor color;

	GitHubLabel(String name, String description, @Nullable String hexColor) {
		this.name = name;
		this.description = description;
		this.hexColor = hexColor;
		JBColor color = null;
		if (StringUtils.hasText(hexColor)) {
			try {
				int i = Integer.parseInt(hexColor, 16);
				color = new JBColor(i, i);
			} catch (NumberFormatException ex) {
			}
		}
		this.color = color;
	}

	static GitHubLabel of(GithubIssueLabel label) {

		String description = label.getName();
		try {
			// 🙄
			description = "" + FieldUtils.readDeclaredField(label, "description", true);
		} catch (RuntimeException | ReflectiveOperationException ignore) {
		}

		return new GitHubLabel(label.getName(), description, label.getColor());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return StringUtils.hasText(description) ? description : name;
	}

	public @Nullable String getHexColor() {
		return hexColor;
	}

	@Override
	public @Nullable Color getColor() {
		return this.color;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitHubLabel that)) {
			return false;
		}
		return name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return name;
	}

}
