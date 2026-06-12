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

package biz.paluch.dap.npm;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.assistant.DependencyLineMarkerProvider;
import biz.paluch.dap.npm.NpmAssistant.NpmInterface;
import biz.paluch.dap.support.MessageBundle;

/**
 * Gutter configurable for NPM dependencies.
 *
 * @author Mark Paluch
 */
public class NpmLineMarkerProvider extends DependencyLineMarkerProvider {

	@Override
	public String getName() {
		return MessageBundle.message("gutter.upgrade-available.name", NpmInterface.INSTANCE.getDisplayName());
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.UPGRADE_NPM_ICON;
	}

}
