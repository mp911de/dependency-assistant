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

package biz.paluch.dap.assistant;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearch;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageWithType;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jspecify.annotations.Nullable;

/**
 * Drives a Dependency Site popup from a {@code DependencyCheck} dialog row:
 * narrows the files to search, runs the {@link DependencySiteSearch}, and
 * presents the result for navigation. The multi-result popup shows a read-only
 * preview of the focused declaration site beside the list.
 *
 * @author Mark Paluch
 */
class DependencySitesPopup {

	private static final long INDEXING_NOTICE_FADEOUT_MILLIS = 5000;

	/**
	 * Shortcut that opens the result in the Find tool window, matching the Find in
	 * Files popup binding ({@code Cmd+Enter} on macOS, {@code Ctrl+Enter}
	 * elsewhere).
	 */
	private static final KeyStroke OPEN_IN_FIND_WINDOW = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
			SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);

	private final Project project;

	private final Disposable parentDisposable;

	private final Runnable onTransferToFindWindow;

	private final Collection<VirtualFile> files;

	/**
	 * @param onTransferToFindWindow invoked when the user hands the results to the
	 * Find tool window, letting the host (the dependency upgrade dialog) close
	 * itself.
	 * @param files the build files to search, the same set the upgrade scan
	 * produced.
	 */
	DependencySitesPopup(Project project, Disposable parentDisposable, Runnable onTransferToFindWindow,
			Collection<VirtualFile> files) {
		this.project = project;
		this.parentDisposable = parentDisposable;
		this.onTransferToFindWindow = onTransferToFindWindow;
		this.files = files;
	}

	/**
	 * Find every site backing the row's version and present it for navigation. The
	 * search runs off the UI thread through a non-blocking read action, so the UI
	 * thread is not blocked.
	 *
	 * <p>The index-backed integrations query the file-type and filename indexes,
	 * which are unavailable during indexing. Rather than wait for indexing to
	 * finish, the find fails fast: if the project is in dumb mode it shows a
	 * lightweight notice anchored at {@code where} and returns. The read expires
	 * with {@code parentDisposable} so a closed dialog aborts a pending find.
	 *
	 * @param candidate the double-clicked row.
	 * @param where the screen anchor for the popup or notice; must not be
	 * {@literal null}.
	 */
	void navigate(UpgradeCandidate candidate, RelativePoint where) {

		if (DumbService.getInstance(project).isDumb()) {
			showIndexingNotice(where);
			return;
		}

		DependencySiteQuery query = candidate.toQuery();
		ReadAction.nonBlocking(() -> findEntries(query))
				.expireWith(parentDisposable)
				.finishOnUiThread(ModalityState.stateForComponent(where.getComponent()), entries -> {

					if (!where.getComponent().isShowing()) {
						return;
					}

					if (entries == null) {
						showIndexingNotice(where);
						return;
					}

					present(entries, where);
				})
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	/**
	 * Resolve the entries for the query inside the non-blocking read. Returns
	 * {@literal null} when the project entered dumb mode before the read acquired
	 * the lock, signalling the caller to show the indexing notice instead.
	 */
	private @Nullable List<DependencySitePresentation> findEntries(DependencySiteQuery query) {

		if (DumbService.getInstance(project).isDumb()) {
			return null;
		}

		return DependencySiteSearch.create(new DependencySiteSearchFunction(project)).find(query, files).stream()
				.map(hit -> DependencySitePresentation.from(hit, project)).toList();
	}

	/**
	 * Show a transient warning balloon anchored at the click site telling the user
	 * the find is unavailable while indexing runs.
	 */
	private void showIndexingNotice(RelativePoint where) {

		JBPopupFactory.getInstance()
				.createHtmlTextBalloonBuilder(MessageBundle.message("dialog.findSites.indexing"), MessageType.WARNING,
						null)
				.setFadeoutTime(INDEXING_NOTICE_FADEOUT_MILLIS)
				.createBalloon()
				.show(where, Balloon.Position.above);
	}

	private void present(List<DependencySitePresentation> entries, RelativePoint where) {

		if (entries.isEmpty()) {
			JBPopupFactory.getInstance().createMessage(MessageBundle.message("dialog.findSites.empty")).show(where);
			return;
		}

		List<DependencySitePresentation> ordered = entries.stream()
				.sorted(Comparator.comparingInt(entry -> entry.finding().role().ordinal())).toList();

		JBList<DependencySitePresentation> list = new JBList<>(new CollectionListModel<>(ordered));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DependencySitePresentationRenderer());
		list.setSelectedIndex(0);

		EditorTextField preview = new EditorTextField("", project, ordered.getFirst().fileType());
		preview.setViewer(true);
		preview.setOneLineMode(false);
		preview.setFontInheritedFromLAF(false);
		updatePreview(preview, ordered.getFirst());

		JBSplitter splitter = new JBSplitter(false, 0.4f);
		splitter.setFirstComponent(new JBScrollPane(list));
		splitter.setSecondComponent(preview);

		JButton openInFind = new JButton(MessageBundle.message("dialog.findSites.openInFind"));
		JBLabel openInFindHint = new JBLabel(KeymapUtil.getKeystrokeText(OPEN_IN_FIND_WINDOW));
		openInFindHint.setEnabled(false);

		JPanel content = new JPanel(new BorderLayout());
		content.add(splitter, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(4)));
		actions.add(openInFindHint);
		actions.add(openInFind);
		content.add(actions, BorderLayout.SOUTH);
		content.setPreferredSize(new Dimension(JBUI.scale(780), JBUI.scale(340)));
		content.registerKeyboardAction(event -> openInFind.doClick(), OPEN_IN_FIND_WINDOW,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, list)
				.setTitle(MessageBundle.message("dialog.findSites.title"))
				.setResizable(true)
				.setTitleIcon(new ActiveIcon(DependencyAssistantIcons.ICON))
				.setMovable(true)
				.setRequestFocus(true)
				.createPopup();

		openInFind.addActionListener(event -> {
			popup.closeOk(null);
			openInFindWindow(ordered);
		});

		list.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				updatePreview(preview, list.getSelectedValue());
			}
		});

		list.addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent event) {
				int index = list.locationToIndex(event.getPoint());
				if (index >= 0) {
					list.setSelectedIndex(index);
				}
			}

		});

		new DoubleClickListener() {

			@Override
			protected boolean onDoubleClick(MouseEvent event) {
				return chooseSelected(list, popup);
			}

		}.installOn(list);

		list.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiersEx() == 0) {
					chooseSelected(list, popup);
				}
			}

		});

		popup.show(where);
	}

	/**
	 * Hand the findings to the Find tool window as usages, so the result can be
	 * browsed and kept open like Find in Files.
	 */
	private void openInFindWindow(List<DependencySitePresentation> entries) {

		Map<SiteRole, UsageType> usageTypes = new EnumMap<>(SiteRole.class);
		Usage[] usages = ReadAction.compute(() -> entries.stream()
				.filter(DependencySitePresentation::isValid)
				.map(entry -> (Usage) new SiteUsage(entry,
						usageTypes.computeIfAbsent(entry.finding().role(), DependencySitesPopup::usageType)))
				.toArray(Usage[]::new));

		if (usages.length == 0) {
			return;
		}

		UsageViewPresentation presentation = new UsageViewPresentation();
		presentation.setTabText(MessageBundle.message("dialog.findSites.title"));
		presentation.setToolwindowTitle(MessageBundle.message("dialog.findSites.title"));
		presentation.setSearchString(MessageBundle.message("dialog.findSites.title"));
		UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages, presentation);

		onTransferToFindWindow.run();
	}

	private static UsageType usageType(SiteRole role) {
		return new UsageType(() -> MessageBundle.message("dialog.findSites.role." + role.name()));
	}

	private boolean chooseSelected(JList<DependencySitePresentation> list, JBPopup popup) {

		DependencySitePresentation selected = list.getSelectedValue();
		if (selected == null) {
			return false;
		}

		popup.closeOk(null);
		navigate(selected);
		return true;
	}

	private void updatePreview(EditorTextField preview, @Nullable DependencySitePresentation entry) {

		if (entry == null) {
			return;
		}

		Document document = EditorFactory.getInstance().createDocument(entry.previewText());
		preview.setNewDocumentAndFileType(entry.fileType(), document);
	}

	private void navigate(DependencySitePresentation entry) {

		PsiElement element = entry.finding().element();
		ReadAction.run(() -> {
			VirtualFile file = element.getContainingFile().getVirtualFile();
			if (element.isValid() && file != null) {
				new OpenFileDescriptor(project, file, element.getTextOffset()).navigate(true);
			}
		});
	}

	private static class DependencySitePresentationRenderer
			extends ColoredListCellRenderer<DependencySitePresentation> {

		@Override
		protected void customizeCellRenderer(JList<? extends DependencySitePresentation> list,
				DependencySitePresentation entry, int index,
				boolean selected, boolean hasFocus) {

			append(MessageBundle.message("dialog.findSites.role." + entry.finding().role().name()) + "  ",
					SimpleTextAttributes.GRAYED_ATTRIBUTES);
			append(entry.label());
			if (!entry.location().isEmpty()) {
				append("  " + entry.location(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
			}
		}

	}

	/**
	 * A usage carrying our own {@link SiteRole} classification, so the Find tool
	 * window groups results under Definition, Version usage, and Consumption rather
	 * than a single {@code Unclassified} node. The usage-type grouping rule honours
	 * {@code UsageWithType} before falling back to element-based providers.
	 */
	private static class SiteUsage extends UsageInfo2UsageAdapter implements UsageWithType {

		private final UsageType usageType;

		SiteUsage(UsageInfo usageInfo, UsageType usageType) {
			super(usageInfo);
			this.usageType = usageType;
		}

		public SiteUsage(DependencySitePresentation entry, UsageType usageType) {
			this(new UsageInfo(entry.finding().element()), usageType);
		}

		@Override
		public UsageType getUsageType() {
			return usageType;
		}

	}

}
