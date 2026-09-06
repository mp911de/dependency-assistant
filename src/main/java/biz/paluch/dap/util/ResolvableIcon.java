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

package biz.paluch.dap.util;

import java.util.Objects;

import javax.swing.Icon;

import com.intellij.openapi.util.text.HtmlChunk;

/**
 * An icon and its reflective field path for Swing and documentation HTML. The
 * loaded icon cannot supply that path, so both are declared together.
 *
 * @author Mark Paluch
 */
public class ResolvableIcon {

	private final Icon icon;

	private final String reference;

	/**
	 * Bind an icon to its documentation reference.
	 *
	 * @param reference the reflective field path, such as
	 * {@code AllIcons.Nodes.Library}.
	 */
	public ResolvableIcon(Icon icon, String reference) {
		this.icon = icon;
		this.reference = reference;
	}

	/**
	 * Render a documentation icon with the Swing icon as fallback.
	 */
	public HtmlChunk asHtml() {
		return HtmlChunk.icon(reference, icon);
	}

	public Icon getIcon() {
		return icon;
	}

	public String getReference() {
		return reference;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		ResolvableIcon that = (ResolvableIcon) obj;
		return Objects.equals(this.icon, that.icon) && Objects.equals(this.reference, that.reference);
	}

	@Override
	public int hashCode() {
		return Objects.hash(icon, reference);
	}

	@Override
	public String toString() {
		return "ResolvableIcon[icon=" + icon + ", reference=" + reference + ']';
	}

}
