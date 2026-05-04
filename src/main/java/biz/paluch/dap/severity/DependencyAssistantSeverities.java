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

package biz.paluch.dap.severity;

import java.util.List;
import javax.swing.*;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * {@link SeveritiesProvider} for dependency update highlighting.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantSeverities extends SeveritiesProvider {

	/**
	 * Text attributes key for newer dependency versions.
	 */
	public static final TextAttributesKey UPGRADE_AVAILABLE_KEY = TextAttributesKey
			.createTextAttributesKey("UPGRADE_AVAILABLE");

	/**
	 * Generic newer-version highlight severity.
	 */
	public static final HighlightSeverity UPGRADE_AVAILABLE = new HighlightSeverity(
			UPGRADE_AVAILABLE_KEY.getExternalName(),
			HighlightSeverity.INFORMATION.myVal + 5, //
			MessageBundle.lazyMessage("newer.severity"), //
			MessageBundle.lazyMessage("newer.severity.capitalized"), //
			MessageBundle.lazyMessage("newer.severity.count.message"));

	/**
	 * Create a new {@code DependencyAssistantSeverities}.
	 */
	public DependencyAssistantSeverities() {
	}

	@Override
	public List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {

		class GENERIC extends HighlightInfoType.HighlightInfoTypeImpl implements HighlightInfoType.Iconable {

			private GENERIC(HighlightSeverity severity, TextAttributesKey attributesKey) {
				super(severity, attributesKey);
			}

			@Override
			public Icon getIcon() {
				return DependencyAssistantIcons.ICON;
			}

		}

		return List.of(new GENERIC(UPGRADE_AVAILABLE, UPGRADE_AVAILABLE_KEY));
	}

}
