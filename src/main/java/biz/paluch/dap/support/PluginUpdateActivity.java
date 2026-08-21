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

package biz.paluch.dap.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.JavaCoroutines;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.Nullable;

/**
 * Shows update notification.
 *
 * @author Mark Paluch
 */
public class PluginUpdateActivity implements ProjectActivity, DumbAware, LightEditCompatible {

	private static final String PLUGIN_ID = "biz.paluch.dependency-assistant";

	private static final String NOTIFICATION_GROUP = "biz.paluch.dependency-assistant.update";

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {
		return JavaCoroutines.suspendJava(jc -> {

			Application application = ApplicationManager.getApplication();
			ApplicationSettings settings = ApplicationSettings.getInstance();
			IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));

			showUpdateNotification(project, application, settings, plugin);

			jc.resume(Unit.INSTANCE);
		}, continuation);
	}

	private void showUpdateNotification(Project project, Application application, ApplicationSettings settings,
			@Nullable IdeaPluginDescriptor plugin) {

		if (plugin == null) {
			return;
		}
		String version = plugin.getVersion();
		String oldVersion = settings.getVersion();
		boolean updated = !version.equals(oldVersion);
		if (!updated) {
			return;
		}
		settings.setVersion(version);

		// collect the recent changes the user hasn't seen yet
		String changes = createChanges(plugin, oldVersion);
		NotificationGroup group = NotificationGroupManager.getInstance()
				.getNotificationGroup(NOTIFICATION_GROUP);

		if (group == null || StringUtils.isEmpty(changes)) {
			return;
		}

		String changesToShow = changes
				.replaceAll("<[/]?(div|h3|p)[^>]*>", "")
				.replaceAll("(?ms)<ul>\\s*", "<ul>")
				.replaceAll("(?ms)\\n[\\s]+", "\n");

		application.invokeLater(() -> {
			Notification notification = group.createNotification(
					MessageBundle.message("notification.plugin-update.title", version),
					changesToShow, NotificationType.INFORMATION);
			Notifications.Bus.notify(notification, project);
		});
	}

	private String createChanges(IdeaPluginDescriptor plugin, @Nullable String oldVersion) {

		String changeNotes = plugin.getChangeNotes();

		if (StringUtils.isEmpty(changeNotes)) {
			return "";
		}

		StringBuilder changes = new StringBuilder();
		Matcher matcher = Pattern.compile("(?ms)<h3[^>]*>(?<version>[0-9.]+).*?</div>")
				.matcher(changeNotes);
		int count = 0;
		while (matcher.find()) {
			if (matcher.group("version").equals(oldVersion)) {
				break;
			}
			count++;
			if (count > 5) {
				break;
			}
			changes.append(matcher.group());
		}

		return changes.toString();
	}

}
