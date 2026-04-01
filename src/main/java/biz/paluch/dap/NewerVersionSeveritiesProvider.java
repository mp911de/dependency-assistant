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
package biz.paluch.dap;

import java.util.Collections;
import java.util.List;

import javax.swing.Icon;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * {@link SeveritiesProvider} to install the severity for dependencies that have a newer version available. The severity
 * is used to apply a custom text attribute.
 *
 * @author Mark Paluch
 */
public class NewerVersionSeveritiesProvider extends SeveritiesProvider {

	public static final TextAttributesKey NEWER_VERSION_KEY = TextAttributesKey.createTextAttributesKey("NEWER_VERSION");

	public static final HighlightSeverity NEWER_VERSION = new HighlightSeverity(NEWER_VERSION_KEY.getExternalName(),
			HighlightSeverity.INFORMATION.myVal + 5, //
			MessageBundle.lazyMessage("newer.severity"), //
			MessageBundle.lazyMessage("newer.severity.capitalized"), //
			MessageBundle.lazyMessage("newer.severity.count.message"));

	public NewerVersionSeveritiesProvider() {}

	public List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {

		class T extends HighlightInfoType.HighlightInfoTypeImpl implements HighlightInfoType.Iconable {
			private T(HighlightSeverity severity, TextAttributesKey attributesKey) {
				super(severity, attributesKey);
			}

			public Icon getIcon() {
				return DependencyAssistantIcons.TRANSPARENT_ICON;
			}
		}

		return Collections.singletonList(new T(NEWER_VERSION, NEWER_VERSION_KEY));
	}

}
