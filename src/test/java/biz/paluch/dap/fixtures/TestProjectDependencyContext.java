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

package biz.paluch.dap.fixtures;

import java.util.List;
import java.util.Objects;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Mark Paluch
 */
public class TestProjectDependencyContext implements ProjectDependencyContext {

	public static final TestProjectDependencyContext INSTANCE = new TestProjectDependencyContext(
			ProjectId.of("foo", "bar"));

	private final ProjectId projectId;

	private final InterfaceAssistant interfaceAssistant;

	public TestProjectDependencyContext(ProjectId projectId) {
		this(projectId, TestInterfaceAssistant.INSTANCE);
	}

	public TestProjectDependencyContext(ProjectId projectId, InterfaceAssistant interfaceAssistant) {
		this.projectId = projectId;
		this.interfaceAssistant = interfaceAssistant;
	}

	@Override
	public InterfaceAssistant getInterfaceAssistant() {
		return interfaceAssistant;
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}

	@Override
	public DependencyCollector scanDependencies(ProgressIndicator indicator) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isVersionElement(PsiElement element) {
		return false;
	}

	@Override
	public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ProjectId getProjectId() {
		return projectId;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return List.of();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof TestProjectDependencyContext that)) {
			return false;
		}
		return Objects.equals(this.projectId, that.projectId) &&
				Objects.equals(this.interfaceAssistant, that.interfaceAssistant);
	}

	@Override
	public int hashCode() {
		return Objects.hash(projectId, interfaceAssistant);
	}

	@Override
	public String toString() {
		return "TestProjectDependencyContext[" +
				"projectId=" + projectId + ", " +
				"interfaceAssistant=" + interfaceAssistant + ']';
	}

}
