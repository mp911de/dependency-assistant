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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewManager.UsageViewStateListener;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageWithType;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Rerunnable, non-PSI usage target for a dependency-site search. The target
 * retains only durable search inputs and resolves its file scope afresh for
 * each Find invocation.
 *
 * @author Mark Paluch
 */
public class DependencyUsageTarget implements UsageTarget, ItemPresentation {

	private final Project project;

	private final BetterPsiManager psiManager;

	private final DependencySiteQuery query;

	private final Supplier<? extends Iterable<VirtualFile>> files;

	DependencyUsageTarget(Project project, DependencySiteQuery query,
			Supplier<? extends Iterable<VirtualFile>> files) {

		this.project = project;
		this.psiManager = BetterPsiManager.getInstance(project);
		this.query = query;
		this.files = files;
	}

	DependencyUsageTarget(Project project, DependencySiteQuery query,
			VirtualFile... files) {
		this(project, query, () -> List.of(files));
	}

	/**
	 * Search the current file scope. Callers must provide read access.
	 */
	public DependencySearchResults findSites() {
		return find(files.get());
	}

	@Override
	public boolean isValid() {
		return !project.isDisposed();
	}

	@Override
	public void findUsages() {
		findUsages(null);
	}

	void findUsages(@Nullable UsageViewStateListener listener) {

		String title = getName();
		UsageViewPresentation presentation = new UsageViewPresentation();
		presentation.setTabText(title);
		presentation.setToolwindowTitle(title);
		presentation.setSearchString(title);
		presentation.setScopeText(project.getName());

		UsageViewManager.getInstance(project).searchAndShowUsages(new UsageTarget[] {this}, this::createUsageSearcher,
				true, true, presentation, listener);
	}

	UsageSearcher createUsageSearcher() {

		Map<SiteRole, UsageType> usageTypes = new EnumMap<>(SiteRole.class);
		for (SiteRole role : SiteRole.values()) {
			usageTypes.put(role, new UsageType(role::getName));
		}

		return processor -> {

			Iterable<VirtualFile> scope = files.get();
			Set<UsageKey> seen = new LinkedHashSet<>();
			for (VirtualFile file : scope) {

				ProgressManager.checkCanceled();
				List<SiteUsage> found = ReadAction.nonBlocking(() -> {

					if (!file.isValid()) {
						return List.<SiteUsage>of();
					}

					DependencySearchResults hits = find(file);
					return hits.stream().map(hit -> {
						UsageInfo usageInfo = new UsageInfo(hit.element());
						UsageKey key = new UsageKey(usageInfo, hit.role(), hit.label());
						return new SiteUsage(key, usageInfo, usageTypes.get(hit.role()));
					}).toList();
				}).inSmartMode(project).executeSynchronously();

				for (SiteUsage usage : found) {
					if (seen.add(usage.getKey()) && !processor.process(usage)) {
						return;
					}
				}
			}
		};
	}

	@Override
	public String getName() {
		return MessageBundle.message("dialog.findSites.title");
	}

	@Override
	public ItemPresentation getPresentation() {
		return this;
	}

	@Override
	public String getPresentableText() {
		return getName();
	}

	@Override
	public Icon getIcon(boolean unused) {
		return DependencyAssistantIcons.ICON;
	}

	private record UsageKey(UsageInfo usageInfo, SiteRole role, String label) {
	}

	private static class SiteUsage extends UsageInfo2UsageAdapter implements UsageWithType {

		private final UsageKey key;

		private final UsageType usageType;

		private SiteUsage(UsageKey key, UsageInfo usageInfo, UsageType usageType) {
			super(usageInfo);
			this.key = key;
			this.usageType = usageType;
		}

		public UsageKey getKey() {
			return key;
		}

		@Override
		public UsageType getUsageType() {
			return usageType;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof SiteUsage siteUsage)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(key, siteUsage.key);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHashCode(key);
		}

	}

	public DependencySearchResults find(Iterable<? extends VirtualFile> files) {

		List<DependencySearchResults> perFile = new ArrayList<>();
		for (VirtualFile file : files) {
			ProgressManager.checkCanceled();
			perFile.add(find(file));
		}

		return DependencySearchResults.concat(perFile);
	}

	public DependencySearchResults find(VirtualFile file) {

		PsiFile psiFile = psiManager.findFile(file);
		if (psiFile == null) {
			return DependencySearchResults.empty();
		}

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, psiFile);
		if (!context.isAvailable()) {
			return DependencySearchResults.empty();
		}

		VersionUpgradeLookup lookup = context.getLookup(psiFile, file);
		DependencySearchResults sites = lookup.search(query);

		// Ecosystems without an explicit search (NPM, Antora, GitHub) fall back
		// to the inline-only find over their declarations.
		return sites.isEmpty() ? inlineDefinitions(psiFile, lookup)
				: sites;
	}

	private DependencySearchResults inlineDefinitions(PsiElement root,
			VersionUpgradeLookup lookup) {

		if (query.artifacts().isEmpty()) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		Set<PsiElement> seen = new HashSet<>();
		for (PsiElement element : SyntaxTraverser.psiTraverser(root)) {

			ArtifactReference reference = lookup.resolveArtifactReference(element);
			if (!reference.isResolved() || !query.artifacts().contains(reference.getArtifactId())) {
				continue;
			}

			ArtifactDeclaration declaration = reference.getDeclaration();
			PsiElement target = declaration.getVersionLiteral() != null ? declaration.getVersionLiteral()
					: declaration.getDeclarationElement();
			if (seen.add(target)) {
				hits.add(DependencySiteSearchHit.declaration(target,
						declaration.isVersionDefined() ? declaration.getVersion().toString() : target.getText()));
			}
		}

		return DependencySearchResults.of(hits);
	}

}
