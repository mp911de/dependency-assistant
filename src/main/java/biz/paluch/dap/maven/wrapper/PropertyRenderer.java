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

package biz.paluch.dap.maven.wrapper;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.PlatformIcons;

/**
 * {@link LookupElementRenderer} to render {@link WrapperProperty} suggestions.
 * 
 * @author Mark Paluch
 */
class PropertyRenderer extends LookupElementRenderer<LookupElement> {

	public static final PropertyRenderer INSTANCE = new PropertyRenderer();

	@Override
	public void renderElement(LookupElement element, LookupElementPresentation presentation) {

		if (!(element.getObject() instanceof PropertyImpl property)) {
			return;
		}

		presentation.setIcon(PlatformIcons.PROPERTY_ICON);
		presentation.setItemText(element.getLookupString());

		TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme()
				.getAttributes(PropertiesHighlighter.PropertiesComponent.PROPERTY_VALUE.getTextAttributesKey());

		presentation.setTailText(property.getValue(), attrs.getForegroundColor());
	}

}
