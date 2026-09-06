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

package biz.paluch.dap.plan;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.StringJoiner;

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
	 * Plugin-private plan fragment flavor. A serialized-object string flavor would
	 * collide with {@link DataFlavor#stringFlavor} and expose XML on plain-text
	 * paste.
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
	 * Snapshot the plan as text and a transferable fragment. Later mutations do not
	 * affect the payload. Use {@link UpgradePlan#withItems} to copy a selection.
	 * The fragment retains the plan scope.
	 */
	Transferable copy(UpgradePlan plan) {
		return new PlanTransferable(plan, service);
	}

	/**
	 * Read a plan fragment, or return {@literal null} if absent or unreadable.
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

	boolean isPlanInClipboard() {
		return copyPasteManager.areDataFlavorsAvailable(PLAN_FLAVOR);
	}

	private static class PlanTransferable implements Transferable {

		private final String text;

		private final String xml;

		public PlanTransferable(UpgradePlan plan, UpgradePlanService service) {
			Content fragment = Content.from(service.getPlan().getContent(), plan);

			this.text = render(plan, service);
			this.xml = JDOMUtil.write(XmlSerializer.serialize(fragment));
		}

		private String render(UpgradePlan plan, UpgradePlanService service) {

			if (plan.size() == 1) {
				return service.getCommitMessage(plan.getItems().getFirst());
			}

			StringJoiner joiner = new StringJoiner("\n");
			for (UpgradePlanItem item : plan) {

				joiner.add(service.getTicketTitle(item));

				if (item.isGroup()) {
					for (ItemDependency dependency : item) {
						String text = " - %s %s -> %s".formatted(dependency.getArtifactId(),
								dependency.getCurrentVersionString(),
								item.getToVersionString());
						joiner.add(text);
					}
				}

				joiner.add("");
			}

			return joiner.toString();
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
