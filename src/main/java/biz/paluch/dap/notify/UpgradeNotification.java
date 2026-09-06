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
import java.util.List;

import biz.paluch.dap.assistant.AppliedUpdate;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.PropertyKey;

/**
 * Immutable wording for applied dependency upgrades.
 *
 * @author Mark Paluch
 * @see AppliedUpdates
 */
public class UpgradeNotification {

	private final AppliedUpdates updates;

	private final Wording wording;

	private final List<HtmlChunk> details;

	private final NotificationType type;

	private UpgradeNotification(AppliedUpdates updates, Wording wording, List<HtmlChunk> details,
			NotificationType type) {

		this.updates = updates;
		this.wording = wording;
		this.details = details;
		this.type = type;
	}

	public static UpgradeNotification applied(AppliedUpdates updates) {
		return new UpgradeNotification(updates, Wording.APPLIED, List.of(), NotificationType.INFORMATION);
	}

	public static UpgradeNotification committed(AppliedUpdates updates) {
		return new UpgradeNotification(updates, Wording.COMMITTED, List.of(), NotificationType.INFORMATION);
	}

	/**
	 * Return wording with warnings for out-of-bounds updates and major version
	 * crossings. The notification becomes a warning if any such entries exist.
	 */
	public UpgradeNotification withFlagged() {

		List<HtmlChunk> extended = new ArrayList<>(details);
		appendFlagged(extended, AppliedUpdate.Flag.COMPLIANCE, "notification.out-of-bounds",
				"notification.out-of-bounds.single");
		appendFlagged(extended, AppliedUpdate.Flag.MAJOR_CROSSING, "notification.major-crossing",
				"notification.major-crossing.single");

		NotificationType extendedType = extended.size() > details.size() ? NotificationType.WARNING : type;
		return new UpgradeNotification(updates, wording, List.copyOf(extended), extendedType);
	}

	public boolean isEmpty() {
		return updates.isEmpty();
	}

	public boolean isSingle() {
		return updates.size() == 1;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {

		if (isEmpty()) {
			return MessageBundle.message("notification.applied.none");
		}

		if (isSingle()) {
			AppliedUpdate update = updates.first();
			if (update.isUpgrade()) {
				return update.getMessage(wording.upgraded);
			}
			if (update.isDowngrade()) {
				return update.getMessage(wording.downgraded);
			}
			return update.getMessage(wording.updated);
		}

		return MessageBundle.message(wording.many, updates.size());
	}

	public String getContent() {

		List<HtmlChunk> paragraphs = new ArrayList<>();
		if (updates.size() > 1) {
			paragraphs.add(HtmlChunk.text(updates.toString()));
		}
		paragraphs.addAll(details);

		if (paragraphs.isEmpty()) {
			return "";
		}

		if (paragraphs.size() == 1) {
			return paragraphs.getFirst().toString();
		}

		HtmlBuilder builder = new HtmlBuilder();
		for (HtmlChunk paragraph : paragraphs) {
			builder.append(HtmlChunk.p().child(paragraph));
		}

		return builder.toString();
	}

	/**
	 * Create a notification without showing it. Warnings remain visible until
	 * dismissed.
	 */
	Notification create(NotificationChannel channel) {

		Notification notification = channel.create(getTitle(), getContent(), type);
		if (type == NotificationType.WARNING) {
			notification.setImportant(true);
		}

		return notification;
	}

	private void appendFlagged(List<HtmlChunk> target, AppliedUpdate.Flag flag,
			@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String headingKey,
			@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String singleKey) {

		List<AppliedUpdate> flagged = updates.stream().filter(update -> update.flag() == flag).toList();
		if (flagged.isEmpty()) {
			return;
		}

		if (isSingle()) {
			target.add(HtmlChunk.text(MessageBundle.message(singleKey)));
			return;
		}

		HtmlChunk.Element list = HtmlChunk.ul();
		for (AppliedUpdate update : flagged) {
			list = list.child(HtmlChunk.li().addText(update.getMessage("notification.out-of-bounds.entry")));
		}

		// headings carry trusted markup from the message bundle
		target.add(new HtmlBuilder().appendRaw(MessageBundle.message(headingKey, flagged.size())).append(list)
				.toFragment());
	}

	enum Wording {

		APPLIED("notification.applied.upgraded", "notification.applied.downgraded",
				"notification.applied.updated", "notification.applied.many"),

		COMMITTED("notification.committed.upgraded", "notification.committed.downgraded",
				"notification.committed.updated", "notification.committed.many");

		@PropertyKey(resourceBundle = MessageBundle.BUNDLE)
		final String upgraded;

		@PropertyKey(resourceBundle = MessageBundle.BUNDLE)
		final String downgraded;

		@PropertyKey(resourceBundle = MessageBundle.BUNDLE)
		final String updated;

		@PropertyKey(resourceBundle = MessageBundle.BUNDLE)
		final String many;

		Wording(@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String upgraded,
				@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String downgraded,
				@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String updated,
				@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String many) {

			this.upgraded = upgraded;
			this.downgraded = downgraded;
			this.updated = updated;
			this.many = many;
		}

	}

}
