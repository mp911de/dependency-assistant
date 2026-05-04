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

package biz.paluch.dap.gradle;

import javax.swing.*;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.gradle.GradleAssistant.GradleInterface;
import biz.paluch.dap.support.UpgradeAvailableLineMarkerProvider;

/**
 * Gutter configurable for Gradle.
 * 
 * @author Mark Paluch
 */
public class GradleLineMarkerProvider extends UpgradeAvailableLineMarkerProvider {

	@Override
	public String getName() {
		return MessageBundle.message("gutter.upgrade-available.name", GradleInterface.INSTANCE.getDisplayName());
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.UPGRADE_GRADLE_ICON;
	}

}
