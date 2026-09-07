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

package biz.paluch.dap.notify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.support.VersionControl;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;

/**
 * Follow-up actions that expire their notification when chosen.
 *
 * @author Mark Paluch
 * @see NotificationBuilder#action(NotificationAction)
 */
public class NotificationActions {

	/**
	 * Undo the current platform undo operation, if available.
	 */
	public static NotificationAction undo(Project project) {

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo"), () -> {
			UndoManager undoManager = UndoManager.getInstance(project);
			if (undoManager.isUndoAvailable(null)) {
				undoManager.undo(null);
			}
		});
	}

	/**
	 * Undo matching steps from an upgrade run, stopping at the first unrelated
	 * step.
	 *
	 * @param commandNames the command names created by the run, one per undo step.
	 * Names are matched against the end of the platform undo description.
	 */
	public static NotificationAction undoAll(Project project, Collection<String> commandNames) {

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo"), () -> {

			UndoManager undoManager = UndoManager.getInstance(project);
			List<String> remaining = new ArrayList<>(commandNames);

			while (!remaining.isEmpty() && undoManager.isUndoAvailable(null)) {

				String next = undoManager.getUndoActionNameAndDescription(null).getSecond();
				if (!consume(remaining, next)) {
					return;
				}
				undoManager.undo(null);
			}
		});
	}

	private static boolean consume(List<String> commandNames, String undoDescription) {

		for (Iterator<String> iterator = commandNames.iterator(); iterator.hasNext();) {
			if (undoDescription.endsWith(iterator.next())) {
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	/**
	 * Save documents and open the commit dialog for the default change list, with a
	 * commit message describing the applied updates.
	 */
	public static NotificationAction commit(Project project, AppliedUpdates updates, VersionControl vcs) {

		String message = new NotificationTextTemplates(project).getCommitMessage(updates);

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.commit"),
				() -> vcs.openCommitDialog(message));
	}

	public static NotificationAction revertFlagged(Runnable revert) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo-out-of-bounds"),
				revert);
	}

	public static NotificationAction push(Runnable push) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.push"), push);
	}

	public static NotificationAction unshelve(Runnable unshelve) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.unshelve"), unshelve);
	}

	public static NotificationAction refreshReleaseMetadata(Runnable refresh) {
		return NotificationAction.createSimpleExpiring(
				MessageBundle.message("notification.action.refresh-releases-metadata"), refresh);
	}

	public static NotificationAction notNow(Runnable dismiss) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.not-now"), dismiss);
	}

	public static NotificationAction notThisWeek(Runnable dismiss) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.not-this-week"),
				dismiss);
	}

	public static NotificationAction stopNagging(Runnable dismiss) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.do-not-show-again"),
				dismiss);
	}

}
