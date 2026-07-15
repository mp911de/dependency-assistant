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

package biz.paluch.dap.assistant.check;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearch;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ListSpeedSearch;
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
 * Navigates the sites participating in a dependency's version: narrows the
 * files to search, runs the {@link DependencySiteSearch}, and presents the
 * result through direct navigation, a multi-result popup, or the Find tool
 * window. The popup shows a read-only preview of the focused declaration site
 * beside the list.
 *
 * <p>Entry points express the caller's intent: {@link #browse} always shows the
 * popup, {@link #navigate} opens a single result directly in the editor, and
 * {@link #openInFindWindow} hands the results to the Find tool window. The
 * upgrade review dialog and the Upgrade Plan tool window share this module.
 *
 * @author Mark Paluch
 */
public class DependencySiteNavigator {

	private static final long INDEXING_NOTICE_FADEOUT_MILLIS = 5000;

	private static final KeyStroke OPEN_IN_FIND_WINDOW = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
			SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);

	private final JBPopupFactory popupFactory = JBPopupFactory.getInstance();

	private final Project project;

	private final Disposable parentDisposable;

	private final Runnable onTransferToFindWindow;

	private final List<VirtualFile> files;

	/**
	 * Create a navigator that leaves its host open when results move to the Find
	 * tool window.
	 *
	 * @param project the project owning the dependency sites.
	 * @param parentDisposable the lifecycle that expires pending searches.
	 * @param files the build files to search, captured when the navigator is
	 * created.
	 */
	public DependencySiteNavigator(Project project, Disposable parentDisposable, Iterable<VirtualFile> files) {
		this(project, parentDisposable, () -> {
		}, files);
	}

	/**
	 * Create a navigator that notifies its host when results move to the Find tool
	 * window.
	 *
	 * @param project the project owning the dependency sites.
	 * @param parentDisposable the lifecycle that expires pending searches.
	 * @param onTransferToFindWindow invoked when the user hands the results to the
	 * Find tool window, letting the host (the dependency upgrade dialog) close
	 * itself.
	 * @param files the build files to search, the same set the upgrade scan
	 * produced, captured when the navigator is created.
	 */
	public DependencySiteNavigator(Project project, Disposable parentDisposable, Runnable onTransferToFindWindow,
			Iterable<VirtualFile> files) {

		List<VirtualFile> scope = new ArrayList<>();
		files.forEach(scope::add);

		this.project = project;
		this.parentDisposable = parentDisposable;
		this.onTransferToFindWindow = onTransferToFindWindow;
		this.files = scope;
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
	 * @param query the dependency-site query to run.
	 * @param where the screen anchor for the popup or notice; must not be
	 * {@literal null}.
	 */
	public void browse(DependencySiteQuery query, RelativePoint where) {
		find(query, where, result -> present(result, where));
	}

	/**
	 * Find every site backing the query and open a single result directly in the
	 * editor; several results are presented like {@link #browse}. Runs and fails
	 * fast like {@link #browse}.
	 *
	 * @param query the dependency-site query to run.
	 * @param where the screen anchor for the popup or notice; must not be
	 * {@literal null}.
	 */
	public void navigate(DependencySiteQuery query, RelativePoint where) {

		find(query, where, result -> {

			if (result.size() == 1) {
				result.first().navigate();
				return;
			}
			present(result, where);
		});
	}

	/**
	 * Find every site backing the query and hand the results to the Find tool
	 * window, skipping the intermediate popup. An empty result shows the no-sites
	 * message at the anchor. Runs and fails fast like {@link #navigate}.
	 *
	 * @param query the dependency-site query to run.
	 * @param where the screen anchor for the empty message or notice; must not be
	 * {@literal null}.
	 */
	public void openInFindWindow(DependencySiteQuery query, RelativePoint where) {

		find(query, where, result -> {

			if (result.isEmpty()) {
				present(result, where);
				return;
			}
			openInFindWindow(result);
		});
	}

	private void find(DependencySiteQuery query, RelativePoint where,
			Consumer<Sites> presenter) {

		if (DumbService.getInstance(project).isDumb()) {
			showIndexingNotice(where);
			return;
		}

		ReadAction.nonBlocking(() -> findSites(query, files))
				.expireWith(parentDisposable)
				.finishOnUiThread(ModalityState.stateForComponent(where.getComponent()), entries -> {

					if (!where.getComponent().isShowing()) {
						return;
					}

					if (DumbService.getInstance(project).isDumb()) {
						showIndexingNotice(where);
						return;
					}

					presenter.accept(entries);
				})
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	private Sites findSites(DependencySiteQuery query,
			Iterable<VirtualFile> files) {

		List<SitePresentation> sites = DependencySiteSearch
				.create(new DependencySiteSearchFunction(project))
				.find(query, files)
				.map(SitePresentation::new)
				.toList();
		return new Sites(sites);
	}

	private void showIndexingNotice(RelativePoint where) {

		popupFactory
				.createHtmlTextBalloonBuilder(MessageBundle.message("dialog.findSites.indexing"), MessageType.WARNING,
						null)
				.setFadeoutTime(INDEXING_NOTICE_FADEOUT_MILLIS)
				.createBalloon()
				.show(where, Balloon.Position.above);
	}

	private void present(Sites result, RelativePoint where) {

		if (result.isEmpty()) {
			popupFactory.createMessage(MessageBundle.message("dialog.findSites.empty")).show(where);
			return;
		}

		JBList<SitePresentation> list = createList(result);
		EditorTextField preview = createPreview(list, result.iterator().next().fileType());

		list.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				updatePreview(preview, list.getSelectedValue());
			}
		});
		list.setSelectedIndex(0);

		JBSplitter splitter = new JBSplitter(false, 0.4f);
		splitter.setFirstComponent(new JBScrollPane(list));
		splitter.setSecondComponent(preview);

		JButton openInFind = new JButton(MessageBundle.message("dialog.findSites.openInFind"));
		JBLabel shortcutHint = new JBLabel(KeymapUtil.getKeystrokeText(OPEN_IN_FIND_WINDOW));
		shortcutHint.setEnabled(false);
		shortcutHint.setBorder(JBUI.Borders.emptyRight(6));

		JPanel content = new JPanel(new BorderLayout());
		content.add(splitter, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, JBUI.scale(4)));
		actions.add(shortcutHint);
		actions.add(openInFind);
		content.add(actions, BorderLayout.SOUTH);
		content.setPreferredSize(new Dimension(JBUI.scale(780), JBUI.scale(340)));
		content.registerKeyboardAction(event -> openInFind.doClick(), OPEN_IN_FIND_WINDOW,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		JBPopup popup = popupFactory.createComponentPopupBuilder(content, list)
				.setTitle(MessageBundle.message("dialog.findSites.title"))
				.setResizable(true)
				.setTitleIcon(new ActiveIcon(DependencyAssistantIcons.ICON))
				.setMovable(true)
				.setRequestFocus(true)
				.setDimensionServiceKey(project, "DependencyAssistant.DependencySitesPopup", false)
				.createPopup();

		openInFind.addActionListener(event -> {
			popup.closeOk(null);
			openInFindWindow(result);
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

	private JBList<SitePresentation> createList(Sequence<SitePresentation> ordered) {

		JBList<SitePresentation> list = new JBList<>(new CollectionListModel<>(ordered.toList()));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new SiteRenderer());
		ListSpeedSearch.installOn(list, SitePresentation::searchableText);

		list.addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent event) {
				int index = list.locationToIndex(event.getPoint());
				if (index >= 0) {
					list.setSelectedIndex(index);
				}
			}

		});
		return list;
	}

	private EditorTextField createPreview(JBList<SitePresentation> list, FileType fileType) {

		EditorTextField preview = new EditorTextField("", project, fileType);
		preview.setViewer(true);
		preview.setOneLineMode(false);
		preview.setFontInheritedFromLAF(false);
		preview.addSettingsProvider(editor -> {
			SitePresentation selected = list.getSelectedValue();
			if (selected != null) {
				selected.highlight(editor);
			}
		});
		return preview;
	}

	private void openInFindWindow(Sites result) {

		Map<SiteRole, UsageType> usageTypes = new EnumMap<>(SiteRole.class);
		Usage[] usages = ReadAction.compute(() -> result.stream()
				.filter(SitePresentation::hasElement)
				.flatMap(entry -> {
					Usage usage = entry.toUsage(usageTypes);
					return usage == null ? Stream.empty() : Stream.of(usage);
				})
				.toArray(Usage[]::new));

		if (usages.length == 0) {
			return;
		}

		String title = MessageBundle.message("dialog.findSites.title");
		UsageViewPresentation presentation = new UsageViewPresentation();
		presentation.setTabText(title);
		presentation.setToolwindowTitle(title);
		presentation.setSearchString(title);

		UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages, presentation);

		onTransferToFindWindow.run();
	}

	private boolean chooseSelected(JList<SitePresentation> list, JBPopup popup) {

		SitePresentation selected = list.getSelectedValue();
		if (selected == null) {
			return false;
		}

		popup.closeOk(null);
		selected.navigate();
		return true;
	}

	private void updatePreview(EditorTextField preview, @Nullable SitePresentation entry) {

		if (entry == null) {
			return;
		}
		entry.updatePreview(preview);
	}

	private class Sites implements Sequence<SitePresentation> {

		private final List<SitePresentation> sites;

		public Sites(List<SitePresentation> sites) {
			this.sites = sites;
		}

		public int size() {
			return sites.size();
		}

		public SitePresentation first() {
			return iterator().next();
		}

		@Override
		public Stream<SitePresentation> stream() {
			return sites.stream();
		}

		@Override
		public Iterator<SitePresentation> iterator() {
			return sites.iterator();
		}

		@Override
		public List<SitePresentation> toList() {
			return sites;
		}

	}

	/**
	 * EDT-safe projection of one located site. Captures display and preview data
	 * during the search read action while retaining a smart pointer for later
	 * navigation and Find-window transfer.
	 */
	class SitePresentation implements Comparable<SitePresentation> {

		private static final int SNIPPET_LIMIT = 60;

		private final SiteRole role;

		private final SmartPsiElementPointer<PsiElement> element;

		private final String label;

		private final String location;

		private final String previewText;

		private final @Nullable TextRange matchRange;

		private final @Nullable Icon icon;

		private final FileType fileType;

		private SitePresentation(DependencySiteSearchHit hit) {

			PsiElement source = hit.element();
			PsiFile psiFile = source.getContainingFile();
			VirtualFile file = psiFile.getVirtualFile();
			Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

			int line = document != null ? document.getLineNumber(source.getTextOffset()) : 0;
			this.role = hit.role();
			this.element = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(source);
			this.label = snippet(hit.label());
			this.location = file != null ? relativeLocation(file, line) : "";
			this.previewText = document != null ? dedentedLines(source, document) : source.getText();
			this.matchRange = matchRange(source.getText(), previewText);
			this.fileType = psiFile.getFileType();
			this.icon = fileType.getIcon();
		}

		@Override
		public int compareTo(SitePresentation other) {
			int roleComparison = role.compareTo(other.role);
			return roleComparison != 0 ? roleComparison : location.compareTo(other.location);
		}

		private String searchableText() {
			return label + " " + location;
		}

		private void renderOn(ColoredListCellRenderer<?> renderer) {
			renderer.setIcon(icon);
			renderer.append(MessageBundle.message("dialog.findSites.role." + role.name()) + "  ",
					SimpleTextAttributes.GRAYED_ATTRIBUTES);
			renderer.append(label);
			if (!location.isEmpty()) {
				renderer.append("  " + location, SimpleTextAttributes.GRAYED_ATTRIBUTES);
			}
		}

		public boolean hasElement() {
			return element.getElement() != null;
		}

		private void updatePreview(EditorTextField preview) {

			Document document = EditorFactory.getInstance().createDocument(previewText);
			preview.setNewDocumentAndFileType(fileType, document);

			Editor editor = preview.getEditor();
			if (editor != null) {
				highlight(editor);
			}
		}

		private void highlight(Editor editor) {

			editor.getMarkupModel().removeAllHighlighters();
			if (matchRange == null || matchRange.getEndOffset() > editor.getDocument().getTextLength()) {
				return;
			}

			editor.getMarkupModel().addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES,
					matchRange.getStartOffset(), matchRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
					HighlighterTargetArea.EXACT_RANGE);
		}

		private void navigate() {

			ReadAction.run(() -> {

				PsiElement source = element.getElement();
				if (source == null) {
					return;
				}

				VirtualFile file = source.getContainingFile().getVirtualFile();
				if (file != null) {
					new OpenFileDescriptor(project, file, source.getTextOffset()).navigate(true);
				}
			});
		}

		private @Nullable Usage toUsage(Map<SiteRole, UsageType> usageTypes) {

			PsiElement source = element.getElement();
			if (source == null) {
				return null;
			}

			UsageType usageType = usageTypes.computeIfAbsent(role,
					it -> new UsageType(() -> MessageBundle.message("dialog.findSites.role." + it.name())));
			return new SiteUsage(new UsageInfo(source), usageType);
		}

		FileType fileType() {
			return fileType;
		}

		private @Nullable TextRange matchRange(String elementText, String preview) {

			if (elementText.isBlank()) {
				return null;
			}

			int index = preview.indexOf(elementText);
			return index >= 0 ? TextRange.from(index, elementText.length()) : null;
		}

		private String dedentedLines(PsiElement source, Document document) {

			int startLine = document.getLineNumber(source.getTextRange().getStartOffset());
			PsiElement statement = enclosingStatement(source, document, startLine);

			int from = document.getLineStartOffset(startLine);
			int to = statement.getTextRange().getEndOffset();
			return dedent(document.getText(new TextRange(from, to)));
		}

		private PsiElement enclosingStatement(PsiElement source, Document document, int startLine) {

			PsiElement statement = source;
			for (PsiElement parent = source.getParent(); parent != null
					&& !(parent instanceof PsiFile); parent = parent.getParent()) {

				if (isPreviewBoundary(parent, statement, document, startLine)) {
					break;
				}
				statement = parent;
			}
			return statement;
		}

		private boolean isPreviewBoundary(PsiElement parent, PsiElement child, Document document, int startLine) {

			if (parent instanceof PsiFile) {
				return true;
			}

			TextRange parentRange = parent.getTextRange();
			if (document.getLineNumber(parentRange.getStartOffset()) != startLine) {
				return true;
			}

			TextRange childRange = child.getTextRange();
			return parentRange.getStartOffset() == childRange.getStartOffset()
					&& parentRange.getEndOffset() > childRange.getEndOffset();
		}

		private String dedent(String text) {

			String[] lines = text.split("\n", -1);
			int common = Integer.MAX_VALUE;
			for (String line : lines) {

				if (line.isBlank()) {
					continue;
				}

				int indent = 0;
				while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
					indent++;
				}
				common = Math.min(common, indent);
			}

			if (common <= 0 || common == Integer.MAX_VALUE) {
				return text;
			}

			StringBuilder result = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {

				String line = lines[i];
				result.append(line.length() >= common ? line.substring(common) : line);
				if (i < lines.length - 1) {
					result.append('\n');
				}
			}

			return result.toString();
		}

		private String relativeLocation(VirtualFile file, int line) {

			String basePath = project.getBasePath();
			String path = file.getPath();
			if (basePath != null && path.startsWith(basePath)) {
				path = path.substring(basePath.length());
				if (path.startsWith("/")) {
					path = path.substring(1);
				}
			}

			return path + ":" + (line + 1);
		}

		private String snippet(String text) {

			String firstLine = text.strip();
			int newline = firstLine.indexOf('\n');
			if (newline >= 0) {
				firstLine = firstLine.substring(0, newline).strip();
			}

			return firstLine.length() > SNIPPET_LIMIT
					? firstLine.substring(0, SNIPPET_LIMIT - 3) + "..."
					: firstLine;
		}

	}

	private class SiteRenderer extends ColoredListCellRenderer<SitePresentation> {

		@Override
		protected void customizeCellRenderer(JList<? extends SitePresentation> list,
				SitePresentation entry, int index,
				boolean selected, boolean hasFocus) {
			entry.renderOn(this);
		}

	}

	static class SiteUsage extends UsageInfo2UsageAdapter implements UsageWithType {

		private final UsageType usageType;

		private SiteUsage(UsageInfo usageInfo, UsageType usageType) {
			super(usageInfo);
			this.usageType = usageType;
		}

		@Override
		public UsageType getUsageType() {
			return usageType;
		}

	}

}
