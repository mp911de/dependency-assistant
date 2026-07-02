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

package biz.paluch.dap.antora;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.antora.AntoraAssistant.AntoraInterface;
import biz.paluch.dap.assistant.editor.DependencyLineMarkerProvider;
import biz.paluch.dap.util.MessageBundle;

/**
 * Gutter configurable for Antora playbook UI bundles.
 *
 * @author Mark Paluch
 */
public class AntoraLineMarkerProvider extends DependencyLineMarkerProvider {

	@Override
	public String getName() {
		return MessageBundle.message("gutter.upgrade-available.name", AntoraInterface.INSTANCE.getDisplayName());
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.UPGRADE_GITHUB_ICON;
	}

}
