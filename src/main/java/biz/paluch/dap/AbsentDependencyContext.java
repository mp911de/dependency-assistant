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

package biz.paluch.dap;

import java.util.List;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.ProjectId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Absent {@link ProjectDependencyContext} implementation that throws exceptions
 * for all operations. Used when no dependency context is available.
 * @author Mark Paluch
 */
enum AbsentDependencyContext implements ProjectDependencyContext {

	ABSENT;

	@Override
	public InterfaceAssistant getInterfaceAssistant() {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public DependencyCollector scanDependencies(ProgressIndicator indicator) {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public boolean isVersionElement(PsiElement element) {
		return false;
	}

	@Override
	public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public ProjectId getProjectId() {
		throw new IllegalStateException("No dependency context available");
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return List.of();
	}

}
