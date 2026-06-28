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

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.text.HtmlChunk;

/**
 * A presentation icon that carries both its Swing rendering and the reflective
 * path the platform re-resolves for documentation HTML.
 *
 * <p>The two halves cannot be derived from each other: a loaded {@link Icon}
 * does not expose the {@code AllIcons.Nodes.Library} style field path that the
 * quick documentation {@code <icon src>} resolver needs. Binding them in one
 * value keeps the registry that declares an icon the single place that also
 * declares its reflective path, so the two cannot drift apart.
 *
 * <p>Implements {@link Icon} by delegating to the wrapped icon, so a
 * {@code ResolvableIcon} can be handed to any Swing surface directly. Callers
 * that downcast to {@link ScalableIcon} (scaling, layering) must use
 * {@link #getIcon()} to reach the underlying icon instead.
 *
 * @author Mark Paluch
 */
public class ResolvableIcon {

	private final Icon icon;

	private final String reference;

	/**
	 * @param icon the Swing icon for components (gutter, combo, lookup, table).
	 * @param reference the reflective field path (e.g.
	 * {@code AllIcons.Nodes.Library} or
	 * {@code biz.paluch.dap.checker.CheckerIcons.HIGH}) the documentation icon
	 * resolver re-resolves; never blank.
	 */
	public ResolvableIcon(Icon icon, String reference) {
		this.icon = icon;
		this.reference = reference;
	}

	/**
	 * Render this icon as a documentation {@link HtmlChunk}, passing the reflective
	 * path with the Swing icon as the resolver fallback.
	 *
	 * @return the icon chunk for embedding in quick documentation HTML.
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
