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

import java.util.function.BiFunction;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Resolves a {@link DependencySiteQuery} against a single build file, returning
 * the declaration sites that match.
 *
 * <p>Delegates to the file's {@link VersionUpgradeLookup}; ecosystems without
 * an explicit site search (NPM, Antora, GitHub Actions) fall back to an
 * inline-only scan over their declarations. An unresolvable or unsupported file
 * yields {@link DependencySearchResults#empty() empty} results.
 *
 * @author Mark Paluch
 */
public class DependencySiteSearchFunction
		implements BiFunction<VirtualFile, DependencySiteQuery, DependencySearchResults> {

	private final Project project;

	private final BetterPsiManager psiManager;

	public DependencySiteSearchFunction(Project project) {
		this.project = project;
		this.psiManager = BetterPsiManager.getInstance(project);
	}

	@Override
	public DependencySearchResults apply(VirtualFile file, DependencySiteQuery query) {

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
		return sites.isEmpty()
				? ArtifactReferenceResolver.inlineDefinitions(psiFile, query, lookup::resolveArtifactReference)
				: sites;
	}

}
