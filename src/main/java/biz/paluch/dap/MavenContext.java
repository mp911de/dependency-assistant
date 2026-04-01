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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Maven project context.
 *
 * @author Mark Paluch
 */
public interface MavenContext {

	/**
	 * Lookup the {@link MavenContext} for the given {@link Project} and {@link VirtualFile}.
	 */
	static MavenContext of(Project project, @Nullable PsiFile file) {

		if (file == null) {
			return EmptyMavenContext.INSTANCE;
		}

		return of(project, file.getVirtualFile());
	}

	/**
	 * Lookup the {@link MavenContext} for the given {@link Project} and {@link VirtualFile}.
	 */
	static MavenContext of(Project project, @Nullable VirtualFile file) {

		MavenProjectsManager projectsManager = MavenContextImpl.projectsManager.computeIfAbsent(project,
				MavenProjectsManager::getInstance);
		if (!projectsManager.isMavenizedProject() || file == null) {
			return EmptyMavenContext.INSTANCE;
		}
		MavenProject mavenProject = projectsManager.findProject(file);
		if (mavenProject == null) {
			return EmptyMavenContext.INSTANCE;
		}

		return MavenContextImpl.contexts.computeIfAbsent(file, it -> {
			return new MavenContextImpl(project, mavenProject, mavenProject.getMavenId());
		});
	}

	/**
	 * Returns whether the context is available.
	 */
	boolean isAvailable();

	/**
	 * Returns the associated Maven id.
	 */
	MavenId getMavenId();

	/**
	 * Execute the given action with the Maven project.
	 */
	<T> T doWithMaven(Function<MavenProject, T> action);

	class MavenContextImpl implements MavenContext {

		static Map<Project, MavenProjectsManager> projectsManager = Collections.synchronizedMap(new WeakHashMap<>());
		static Map<VirtualFile, MavenContext> contexts = Collections.synchronizedMap(new WeakHashMap<>());

		private final Project project;
		private final MavenProject mavenProject;
		private final MavenId id;

		public MavenContextImpl(Project project, MavenProject mavenProject, MavenId id) {
			this.project = project;
			this.mavenProject = mavenProject;
			this.id = id;
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public MavenId getMavenId() {
			return id;
		}

		@Override
		public <T> T doWithMaven(Function<MavenProject, T> action) {
			return action.apply(mavenProject);
		}

	}

	/**
	 * Absent Maven context.
	 */
	enum EmptyMavenContext implements MavenContext {

		INSTANCE;

		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public MavenId getMavenId() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public <T> T doWithMaven(Function<MavenProject, T> action) {
			throw new IllegalStateException("Maven Context not available");
		}

	}
}
