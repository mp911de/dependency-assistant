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

import java.util.Map;

import javax.swing.Icon;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

/**
 * Exposes {@link NewerVersionSeveritiesProvider#NEWER_VERSION_KEY} in Color Scheme so users can customise the highlight
 * applied to dependency versions that have a newer release available.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantColorSettingsPage implements ColorSettingsPage {

	private static final AttributesDescriptor[] DESCRIPTORS = { new AttributesDescriptor(
			MessageBundle.lazyMessage("newer.severity"), NewerVersionSeveritiesProvider.NEWER_VERSION_KEY) };

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
				    <version><NEWER>6.1.0</NEWER></version>
				</dependency>
				""";
	}

	@Override
	public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
		return Map.of("NEWER", NewerVersionSeveritiesProvider.NEWER_VERSION_KEY);
	}

}
