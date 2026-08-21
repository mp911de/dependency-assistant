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

package biz.paluch.dap.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "DependencyAssistantApplicationSettings", storages = @Storage("dependency-assistant.xml"))
@SuppressWarnings("SameNameButDifferent")
public class ApplicationSettings implements PersistentStateComponent<ApplicationSettings.State>, ModificationTracker {

	private final State state = new State();

	private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

	@NotNull
	public static ApplicationSettings getInstance() {
		return ApplicationManager.getApplication().getService(ApplicationSettings.class);
	}

	@Nullable
	@Override
	public State getState() {
		return state;
	}

	@Override
	public void loadState(@NotNull State state) {
		XmlSerializerUtil.copyBean(state, this.state);
	}

	@Override
	public long getModificationCount() {
		return modificationTracker.getModificationCount();
	}

	public static class State {

		private @Nullable String pluginVersion;

		public @Nullable String getPluginVersion() {
			return pluginVersion;
		}

		public void setPluginVersion(String pluginVersion) {
			this.pluginVersion = pluginVersion;
		}

	}

	public @Nullable String getVersion() {
		return state.getPluginVersion();
	}

	public void setVersion(String version) {
		modificationTracker.incModificationCount();
		state.setPluginVersion(version);
	}

}
