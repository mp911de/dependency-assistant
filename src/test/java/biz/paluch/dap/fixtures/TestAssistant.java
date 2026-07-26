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

package biz.paluch.dap.fixtures;

import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class TestAssistant implements DependencyAssistant {

	public static TestAssistant INSTANCE = new TestAssistant();

	@Override
	public String getId() {
		return "test";
	}

	@Override
	public String getDisplayName() {
		return getId();
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}

	@Override
	public InterfaceAssistant getInterfaceAssistant() {
		return TestInterfaceAssistant.INSTANCE;
	}

	@Override
	public boolean supports(Project project) {
		return false;
	}

	@Override
	public boolean supports(PsiFile file) {
		return false;
	}

	@Override
	public List<PsiFile> enumerate(Project project) {
		return List.of();
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {

	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {
		return null;
	}

}
