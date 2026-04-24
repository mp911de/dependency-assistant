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
package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ReleasesSuggestionProvider;
import biz.paluch.dap.support.ReleasesSuggestionProvider.CompletionMetadata;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor that suggests available versions for the version
 * segment of a Gradle dependency string literal in Groovy DSL or Kotlin DSL
 * files.
 *
 * @author Mark Paluch
 */
public class ReleaseVersionCompletionContributor extends CompletionContributor {

	private final ReleasesSuggestionProvider provider = new ReleasesSuggestionProvider(element -> {

		if (element instanceof LeafPsiElement) {
			element = element.getParent();
		}

		VersionUpgradeLookupService lookupService = VersionUpgradeLookupService.create(element);
		ArtifactReference artifactReference = lookupService.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactVersion version = lookupService.getCurrentVersion(artifactReference.getArtifactId());
		if (version != null) {
			return new CompletionMetadata(artifactReference.getArtifactId(), version);
		}
		return new CompletionMetadata(artifactReference.getArtifactId(),
				artifactReference.getDeclaration().getVersion());
	});

	@Override
	public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {

		PsiFile file = parameters.getOriginalFile();
		if (!GradleUtils.isGradleFile(file.getVirtualFile())) {
			return;
		}
		provider.addCompletionVariants(parameters, new ProcessingContext(), result);
	}

}
