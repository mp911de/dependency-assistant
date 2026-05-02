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

package biz.paluch.dap.assertions;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;
import org.assertj.core.presentation.Representation;

/**
 * AssertJ {@link Representation} for Dependency Assistant line markers.
 *
 * <p>Failure messages involving gutter assertions are otherwise difficult to
 * inspect because IntelliJ renderers expose little useful state through their
 * default {@code toString()}. This representation unwraps
 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer} instances and includes
 * the marker tooltip plus the associated PSI element text when available.
 *
 * <p>Unsupported values are rendered with their regular {@code toString()}
 * representation so this formatter can be registered safely with AssertJ's
 * standard representation.
 *
 * @author Mark Paluch
 */
public class LineMarkerInfoRepresentation implements Representation {

	/**
	 * Returns a string representation of the given object for assertion failure
	 * messages.
	 */
	@Override
	public String toStringOf(Object object) {

		if (object instanceof LineMarkerInfo<?> lineMarkerInfo) {

			PsiElement element = lineMarkerInfo.getElement();
			if (element != null) {
				return String.format("%s for %s (Element %s: '%s')", lineMarkerInfo.getClass().getSimpleName(),
						lineMarkerInfo.getLineMarkerTooltip(), element.getClass().getName(), element.getText());
			}

			return String.format("%s for %s", lineMarkerInfo.getClass().getSimpleName(),
					lineMarkerInfo.getLineMarkerTooltip());
		}

		if (object instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer) {
			LineMarkerInfo<?> lineMarkerInfo = renderer.getLineMarkerInfo();
			return toStringOf(lineMarkerInfo);
		}

		return object.toString();
	}

	/**
	 * Returns the unambiguous representation used by AssertJ failure messages.
	 */
	@Override
	public String unambiguousToStringOf(Object object) {
		return toStringOf(object);
	}

	/**
	 * Returns a priority higher than AssertJ's default representation so gutter
	 * marker formatting wins for the supported IntelliJ types.
	 */
	@Override
	public int getPriority() {
		return DEFAULT_PRIORITY + 1;
	}

}
