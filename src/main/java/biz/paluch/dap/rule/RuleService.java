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

/**
 * Project-level service that discovers, parses, and resolves the
 * {@code dependencyfile.json} Dependency Rules descriptor.
 *
 * <p>The descriptor is located by an ordered chain, first match wins, never
 * merged: the project root, then {@code .github/}, then (only when the project
 * is trusted) the immediate parent directory and the user home directory.
 * Locations outside the project directory are read only for trusted projects so
 * that opening an untrusted project cannot pull policy from the developer's
 * machine.
 *
 * <p>The parsed {@link DependencyRules} aggregate is cached and recomputed
 * lazily. Edits to an in-project descriptor invalidate the cache so the next
 * {@link #resolve(ArtifactId, String, ArtifactVersion) resolve} reflects the
 * change without an IDE restart. Descriptors outside the project root are
 * neither watched nor auto-reloaded.
 *
 * @author Mark Paluch
 */
public class RuleService implements Disposable, DependencyRuleResolver {

	private static final String FILE_NAME = "dependencyfile.json";

	private static final String GITHUB_DIRECTORY = ".github";

	private final Project project;

	private final DescriptorChangeListener listener;

	RuleService(Project project) {
		this.project = project;
		this.listener = new DescriptorChangeListener(project);
		project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener);
	}

	/**
	 * Return the rule service for the given project.
	 * @param project the project; must not be {@literal null}.
	 * @return the project rule service.
	 */
	public static RuleService getInstance(Project project) {
		return project.getService(RuleService.class);
	}

	@Override
	public DependencyRule resolve(ArtifactId artifactId, Project project, @Nullable VirtualFile file,
			Versioned projectVersion) {
		return rules().resolve(artifactId, currentBranchName(project, file), projectVersion.orElseGet(() -> null));
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

		Repository repository = VcsRepositoryManager.getInstance(project)
				.getRepositoryForFileQuick(file);
		return repository != null ? repository.getCurrentBranchName() : null;
	}

	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		return rules().resolve(artifactId, branchName, projectVersion);
	}

	/**
	 * Install the active rules directly, bypassing descriptor discovery.
	 *
	 * @param rules the rules to use, or {@literal null} to restore discovery.
	 */
	public void setRules(@Nullable DependencyRules rules) {
		this.listener.rules = rules;
	}

	private Rules rules() {

		Rules current = listener.rules;
		if (current == null) {
			current = load();
			listener.rules = current;
		}
		return current;
	}

	private Rules load() {

		VirtualFile descriptor = findDescriptor();
		if (descriptor == null) {
			return Rules.absent();
		}
		return ReadAction.compute(() -> parse(descriptor));
	}

	private @Nullable VirtualFile findDescriptor() {

		String basePath = this.project.getBasePath();
		if (basePath == null) {
			return null;
		}

		LocalFileSystem fileSystem = LocalFileSystem.getInstance();
		Path home = Path.of(System.getProperty("user.home"));
		boolean trusted = TrustedProjects.isProjectTrusted(this.project);

		for (Path candidate : candidatePaths(Path.of(basePath), home, trusted)) {

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
	 * existing location wins; candidates are never merged.
	 *
	 * @param projectRoot the project root directory.
	 * @param userHome the user home directory.
	 * @param trusted whether the project is trusted.
	 * @return the ordered candidate paths.
	 */
	static List<Path> candidatePaths(Path projectRoot, Path userHome, boolean trusted) {

		List<Path> paths = new ArrayList<>();
		paths.add(projectRoot.resolve(FILE_NAME));
		paths.add(projectRoot.resolve(GITHUB_DIRECTORY).resolve(FILE_NAME));

		if (trusted) {

			Path parent = projectRoot.getParent();
			if (parent != null) {
				paths.add(parent.resolve(FILE_NAME));
			}
			paths.add(userHome.resolve(FILE_NAME));
		}
		return paths;
	}

	private Rules parse(VirtualFile descriptor) {

		PsiFile psiFile = PsiManager.getInstance(this.project).findFile(descriptor);
		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) {
			return Rules.absent();
		}

		return new RuleParser(jsonFile).parse();
	}

	@Override
	public void dispose() {
		this.listener.rules = null;
	}

	/**
	 * Invalidates the cache when the in-project descriptor is created, edited,
	 * deleted, moved, or renamed. Descriptors outside the project root are not
	 * watched.
	 */
	static class DescriptorChangeListener implements BulkFileListener {

		private final Project project;

		public volatile @Nullable Rules rules;

		public DescriptorChangeListener(Project project) {
			this.project = project;
		}

		@Override
		public void after(List<? extends VFileEvent> events) {

			Set<Path> descriptors = watchedDescriptors();
			if (descriptors.isEmpty()) {
				return;
			}

			for (VFileEvent event : events) {
				if (touchesDescriptor(event, descriptors)) {
					invalidate();
					return;
				}
			}
		}

		private void invalidate() {
			this.rules = null;
			DaemonCodeAnalyzer.getInstance(this.project).restart();
		}

		private boolean touchesDescriptor(VFileEvent event, Set<Path> descriptors) {

			if (event instanceof VFilePropertyChangeEvent rename
					&& VirtualFile.PROP_NAME.equals(rename.getPropertyName())) {

				VirtualFile parent = rename.getFile().getParent();
				return parent != null && watchedDirectories().contains(normalize(parent.getPath()))
						&& (FILE_NAME.equals(rename.getOldValue()) || FILE_NAME.equals(rename.getNewValue()));
			}
			return descriptors.contains(normalize(event.getPath()));
		}

		private Set<Path> watchedDescriptors() {

			Path root = projectRoot();
			if (root == null) {
				return Set.of();
			}
			return Set.of(normalize(root.resolve(FILE_NAME)),
					normalize(root.resolve(GITHUB_DIRECTORY).resolve(FILE_NAME)));
		}

		private Set<Path> watchedDirectories() {

			Path root = projectRoot();
			if (root == null) {
				return Set.of();
			}
			return Set.of(normalize(root), normalize(root.resolve(GITHUB_DIRECTORY)));
		}

		private @Nullable Path projectRoot() {

			String basePath = project.getBasePath();
			return basePath != null ? normalize(Path.of(basePath)) : null;
		}

		private Path normalize(String path) {
			return normalize(Path.of(path));
		}

		private Path normalize(Path path) {
			return path.toAbsolutePath().normalize();
		}

	}

}
