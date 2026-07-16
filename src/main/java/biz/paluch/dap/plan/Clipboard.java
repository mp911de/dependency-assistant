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

package biz.paluch.dap.plan;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;
import java.util.StringJoiner;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jspecify.annotations.Nullable;

/**
 * Clipboard payload for planned upgrades.
 *
 * @author Mark Paluch
 */
class Clipboard {

	/**
	 * Flavor carrying the XML-serialized {@link Content} fragment. The mime type
	 * must be plugin-private: {@code DataFlavor(Class, String)} would produce the
	 * serialized-object mime and collapse into {@link DataFlavor#stringFlavor},
	 * hijacking plain-text paste with the XML payload.
	 */
	static final DataFlavor PLAN_FLAVOR = new DataFlavor(
			"application/x-dependency-assistant-upgrade-plan;class=java.lang.String",
			"Dependency Assistant Upgrade Plan");

	private static final Logger LOG = Logger.getInstance(Clipboard.class);

	private final UpgradePlanService service;

	private final CopyPasteManager copyPasteManager = CopyPasteManager.getInstance();

	public Clipboard(UpgradePlanService service) {
		this.service = service;
	}

	/**
	 * Create the clipboard payload for the given plan: the copy text plus the plan
	 * fragment (item states and scope paths), both rendered at copy time so later
	 * plan mutations do not leak into the clipboard. Callers narrow to a selection
	 * through {@link UpgradePlan#withItems}; the fragment always carries the plan's
	 * scope.
	 */
	Transferable copy(UpgradePlan plan) {
		Content fragment = Content.from(service.getPlan().getContent(), plan);
		return new PlanTransferable(toText(plan.toList()),
				JDOMUtil.write(XmlSerializer.serialize(fragment)));
	}

	/**
	 * Read a copied plan fragment from the clipboard.
	 *
	 * @return the plan fragment, or {@literal null} when the clipboard carries none
	 * or an unreadable one.
	 */
	@Nullable
	Content paste() {

		Object xml = copyPasteManager.getContents(PLAN_FLAVOR);
		if (!(xml instanceof String string)) {
			return null;
		}

		try {
			return XmlSerializer.deserialize(JDOMUtil.load(string), Content.class);
		} catch (Exception e) {
			LOG.warn("Cannot read copied plan items", e);
			return null;
		}
	}

	/**
	 * Return whether the clipboard carries a copied plan fragment.
	 */
	boolean isPlanInClipboard() {
		return copyPasteManager.areDataFlavorsAvailable(PLAN_FLAVOR);
	}

	/**
	 * Render the copy text. A single item reads as a commit message: the rendered
	 * template, the member list for groups, and the closes-reference when a ticket
	 * is linked. Several items read as a titled list separated by blank lines,
	 * without closes-references. Review copies pass no service; they carry no
	 * tickets to reference.
	 */
	private String toText(List<UpgradePlanItem> items) {

		if (items.size() == 1) {
			return service.getCommitMessage(items.getFirst());
		}

		StringJoiner joiner = new StringJoiner("\n\n");
		for (UpgradePlanItem item : items) {
			joiner.add(service.getTicketTitle(item) + memberLines(item));
		}

		return joiner.toString();
	}

	/**
	 * Render the member list for a group item, one {@code " - "}-prefixed line per
	 * member; empty for a single-member item whose title already carries the
	 * coordinates.
	 */
	private static String memberLines(UpgradePlanItem item) {

		List<Dependency> members = item.getMembers();
		if (members.size() < 2) {
			return "";
		}

		StringBuilder text = new StringBuilder();
		for (Dependency member : members) {
			text.append("\n - ").append(item.getMemberArtifactId(member))
					.append(' ').append(item.getMemberFromVersion(member)).append(" -> ").append(item.getToVersion());
		}

		return text.toString();
	}

	private static class PlanTransferable implements Transferable {

		private final String text;

		private final String xml;

		PlanTransferable(String text, String xml) {
			this.text = text;
			this.xml = xml;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {PLAN_FLAVOR, DataFlavor.stringFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return PLAN_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {

			if (PLAN_FLAVOR.equals(flavor)) {
				return xml;
			}
			if (DataFlavor.stringFlavor.equals(flavor)) {
				return text;
			}
			throw new UnsupportedFlavorException(flavor);
		}

	}

}
