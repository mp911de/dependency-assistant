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

import java.util.Map;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

/**
 * Exposes {@link DependencyAssistantSeverities#UPGRADE_AVAILABLE_KEY} in Color
 * Scheme so users can customise the highlight applied to dependency versions
 * that have a newer release available.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantColorSettingsPage implements ColorSettingsPage {

	public static final AttributesDescriptor UPGRADE_AVAILABLE = new AttributesDescriptor(
			MessageBundle.lazyMessage("severity.upgrade.available"),
			DependencyAssistantSeverities.UPGRADE_AVAILABLE_KEY);

	public static final AttributesDescriptor UPGRADE_SUGGESTION = new AttributesDescriptor(
			MessageBundle.lazyMessage("severity.upgrade.suggestion"),
			DependencyAssistantSeverities.UPGRADE_SUGGESTION_KEY);

	private static final AttributesDescriptor[] DESCRIPTORS = {UPGRADE_AVAILABLE, UPGRADE_SUGGESTION,};

	@Override
	public AttributesDescriptor[] getAttributeDescriptors() {
		return DESCRIPTORS;
	}

	@Override
	public ColorDescriptor[] getColorDescriptors() {
		return ColorDescriptor.EMPTY_ARRAY;
	}

	@Override
	public SyntaxHighlighter getHighlighter() {
		return new XmlFileHighlighter();
	}

	@Override
	public String getDisplayName() {
		return MessageBundle.message("plugin.name");
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.ICON;
	}

	@Override
	public String getDemoText() {
		return """
				<dependency>
				    <groupId>org.springframework</groupId>
				    <artifactId>spring-core</artifactId>
				    <version><UPGRADE_AVAILABLE>6.1.0</UPGRADE_AVAILABLE></version>
				</dependency>

				<dependency>
				    <groupId>org.springframework</groupId>
				    <artifactId>spring-core</artifactId>
				    <version><UPGRADE_SUGGESTION>6.1.0</UPGRADE_SUGGESTION></version>
				</dependency>
				""";
	}

	@Override
	public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
		return Map.of("UPGRADE_AVAILABLE", DependencyAssistantSeverities.UPGRADE_AVAILABLE_KEY,
				"UPGRADE_SUGGESTION", DependencyAssistantSeverities.UPGRADE_SUGGESTION_KEY);
	}

}
