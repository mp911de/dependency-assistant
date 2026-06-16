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

package biz.paluch.dap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;

/**
 * Project-level service that discovers and resolves rules from a
 * {@code dependencyfile.json} descriptor.
 *
 * <p>Descriptors are discovered in a fixed order: the project root, the
 * project's {@code .idea/} directory, and, for trusted projects only, the
 * immediate parent directory and the user home directory. The first descriptor
 * wins; descriptors are never merged.
 *
 * <p>Rules are loaded lazily and cached. Changes to in-project descriptors
 * invalidate the cache and restart highlighting. Descriptors outside the
 * project are intentionally not watched.
 *
 * @author Mark Paluch
 */
public class DependencyfileService implements Disposable, DependencyRuleService {

	public static final String FILE_NAME = "dependencyfile.json";

	static final boolean JSON_PRESENT = ClassUtils.isPresent("com.intellij.json.psi.JsonFile",
			DependencyfileService.class.getClassLoader());

	private final Project project;

	private volatile @Nullable Rules rules;

	private volatile @Nullable Rules ruleOverride;

	DependencyfileService(Project project) {
		this.project = project;

		if (JSON_PRESENT) {
			DescriptorChangeListener listener = new DescriptorChangeListener(project, this::invalidate);
			project.getMessageBus()
					.connect(this)
					.subscribe(VirtualFileManager.VFS_CHANGES, listener);
		}
	}

	/**
	 * Return the rule service for the given project.
	 * @param project the project; must not be {@literal null}.
	 * @return the project rule service.
	 */
	public static DependencyfileService getInstance(Project project) {
		return project.getService(DependencyfileService.class);
	}

	/**
	 * Return the active {@code dependencyfile.json} descriptor, the one rules are
	 * resolved from, or {@literal null} when none is discovered (or JSON support is
	 * absent).
	 *
	 * @return the discovered descriptor file, or {@literal null} if none exists.
	 */
	public @Nullable VirtualFile getDescriptor() {
		return JSON_PRESENT ? findDescriptor() : null;
	}

	@Override
	public DependencyRule resolve(ArtifactId artifactId, @Nullable VirtualFile file, Versioned projectVersion) {
		return rules().resolve(artifactId, currentBranchName(this.project, file), projectVersion.orElseGet(() -> null));
	}

	@Override
	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		return rules().resolve(artifactId, branchName, projectVersion);
	}

	/**
	 * Install the active rules directly, bypassing descriptor discovery.
	 * @param rules the rules to use, or {@literal null} to restore discovery.
	 */
	public void setRules(@Nullable Rules rules) {
		this.ruleOverride = rules;
		this.rules = null;
		restartAnalyzer();
	}

	private Rules rules() {
		Rules rules = this.ruleOverride;
		if (rules != null) {
			return rules;
		}

		rules = this.rules;
		if (rules == null) {
			rules = loadRules();
			this.rules = rules;
		}
		return rules;
	}

	private Rules loadRules() {
		if (JSON_PRESENT) {
			VirtualFile descriptor = findDescriptor();
			return (descriptor != null ? ReadAction.compute(() -> parseDescriptor(descriptor)) : Rules.absent());
		}
		return Rules.absent();
	}

	private @Nullable VirtualFile findDescriptor() {
		String basePath = this.project.getBasePath();
		if (basePath == null) {
			return null;
		}

		Path projectRoot = Path.of(basePath);
		Path userHome = Path.of(System.getProperty("user.home"));
		boolean trusted = TrustedProjects.isProjectTrusted(this.project);
		LocalFileSystem fileSystem = LocalFileSystem.getInstance();

		for (Path candidate : candidatePaths(projectRoot, userHome, trusted)) {
			VirtualFile file = fileSystem.refreshAndFindFileByNioFile(candidate);
			if (file != null && file.isValid() && !file.isDirectory()) {
				return file;
			}
		}

		return null;
	}

	/**
	 * Return the ordered descriptor candidate paths for the given project root.
	 *
	 * <p>In-project locations are always searched; the parent directory and the
	 * user home directory are searched only when the project is trusted. The first
	 * existing descriptor wins; candidates are never merged.
	 * @param projectRoot the project root directory.
	 * @param userHome the user home directory.
	 * @param trusted whether the project is trusted.
	 * @return the ordered candidate paths.
	 */
	static List<Path> candidatePaths(Path projectRoot, Path userHome, boolean trusted) {
		List<Path> paths = new ArrayList<>();
		paths.add(projectRoot.resolve(FILE_NAME));
		paths.add(projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER).resolve(FILE_NAME));

		if (trusted) {
			Path parent = projectRoot.getParent();
			if (parent != null) {
				paths.add(parent.resolve(FILE_NAME));
			}
			paths.add(userHome.resolve(FILE_NAME));
		}

		return paths;
	}

	private Rules parseDescriptor(VirtualFile descriptor) {
		PsiFile psiFile = PsiManager.getInstance(this.project).findFile(descriptor);
		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject)) {
			return Rules.absent();
		}

		return new RuleParser(jsonFile).parse();
	}

	private void invalidate() {
		this.rules = null;
		restartAnalyzer();
	}

	private void restartAnalyzer() {
		DaemonCodeAnalyzer.getInstance(this.project).restart("Dependencyfile  changed");
	}

	@Override
	public void dispose() {
		this.rules = null;
		this.ruleOverride = null;
	}

	/**
	 * Detect the active branch name for the supplied file, degrading to
	 * {@literal null} when no repository governs the file or no DVCS integration is
	 * present.
	 */
	private static @Nullable String currentBranchName(Project project, @Nullable VirtualFile file) {
		if (file == null) {
			return null;
		}

		Repository repository = VcsRepositoryManager.getInstance(project).getRepositoryForFileQuick(file);
		return (repository != null ? repository.getCurrentBranchName() : null);
	}

	/**
	 * Invalidates the cache when an in-project descriptor is created, edited,
	 * deleted, moved, or renamed. Descriptors outside the project root are not
	 * watched.
	 */
	static class DescriptorChangeListener implements BulkFileListener {

		private final Set<Path> descriptorPaths;

		private final Set<Path> descriptorDirectories;

		private final Runnable invalidationCallback;

		DescriptorChangeListener(Project project, Runnable invalidationCallback) {
			Path projectRoot = projectRoot(project);
			this.descriptorPaths = (projectRoot != null ? descriptorPaths(projectRoot) : Set.of());
			this.descriptorDirectories = (projectRoot != null ? descriptorDirectories(projectRoot) : Set.of());
			this.invalidationCallback = invalidationCallback;
		}

		@Override
		public void after(List<? extends VFileEvent> events) {
			if (this.descriptorPaths.isEmpty()) {
				return;
			}

			for (VFileEvent event : events) {
				if (touchesDescriptor(event)) {
					this.invalidationCallback.run();
					return;
				}
			}
		}

		private boolean touchesDescriptor(VFileEvent event) {
			if (event instanceof VFilePropertyChangeEvent propertyChange
					&& VirtualFile.PROP_NAME.equals(propertyChange.getPropertyName())) {

				VirtualFile parent = propertyChange.getFile().getParent();
				return parent != null
						&& this.descriptorDirectories.contains(normalize(parent.getPath()))
						&& (FILE_NAME.equals(propertyChange.getOldValue())
								|| FILE_NAME.equals(propertyChange.getNewValue()));
			}

			return this.descriptorPaths.contains(normalize(event.getPath()));
		}

		private static Set<Path> descriptorPaths(Path projectRoot) {
			return Set.of(
					normalize(projectRoot.resolve(FILE_NAME)),
					normalize(projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER).resolve(FILE_NAME)));
		}

		private static Set<Path> descriptorDirectories(Path projectRoot) {
			return Set.of(
					normalize(projectRoot),
					normalize(projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER)));
		}

		private static @Nullable Path projectRoot(Project project) {
			String basePath = project.getBasePath();
			return (basePath != null ? normalize(Path.of(basePath)) : null);
		}

		private static Path normalize(String path) {
			return normalize(Path.of(path));
		}

		private static Path normalize(Path path) {
			return path.toAbsolutePath().normalize();
		}

	}

}
