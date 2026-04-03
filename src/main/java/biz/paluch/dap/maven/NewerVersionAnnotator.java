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
package biz.paluch.dap.maven;

import biz.paluch.dap.NewerVersionSeveritiesProvider;
import biz.paluch.dap.support.NewerVersionAnnotatorSupport;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import com.intellij.psi.PsiElement;

/**
 * Annotator that applies a highlight to version text in a {@code pom.xml} when a newer version is available in the
 * cache.
 * <p>
 * Complements {@link NewerVersionLineMarkerProvider}: the gutter icon provides a click target while this annotation
 * draws the reader's eye directly to the outdated version string in the editor.
 *
 * @author Mark Paluch
 */
public class NewerVersionAnnotator extends NewerVersionAnnotatorSupport {

	public NewerVersionAnnotator() {
		super(UpdateMavenDependenciesIntention.INSTANCE, NewerVersionSeveritiesProvider.NEWER_VERSION_MAVEN);
	}

	@Override
	protected VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element) {
		return VersionUpgradeLookupService.create(element);
	}

}
