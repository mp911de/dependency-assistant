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

package biz.paluch.dap.assistant;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.DependencySource;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SaveStateRefresh}.
 *
 * @author Mark Paluch
 */
class SaveStateRefreshUnitTests {

	private final StateService service = new StateService();

	private final SaveStateRefresh refresh = new SaveStateRefresh(null, service);

	private final ProgressIndicator indicator = new TestProgressIndicator();

	@Test
	void groupByOwnerAssignsFilesToTheirSupportingAssistants() {

		PsiFile alphaFile = stubPsiFile();
		PsiFile betaFile = stubPsiFile();
		FakeAssistant alpha = new FakeAssistant("alpha", alphaFile);
		FakeAssistant beta = new FakeAssistant("beta", betaFile);

		Map<DependencyAssistant, List<PsiFile>> grouped = SaveStateRefresh
				.groupByOwner(List.of(alpha, beta), List.of(alphaFile, betaFile));

		assertThat(grouped).containsOnlyKeys(alpha, beta);
		assertThat(grouped.get(alpha)).containsExactly(alphaFile);
		assertThat(grouped.get(beta)).containsExactly(betaFile);
	}

	@Test
	void groupByOwnerExcludesUnsupportedFiles() {

		PsiFile owned = stubPsiFile();
		PsiFile orphan = stubPsiFile();
		FakeAssistant alpha = new FakeAssistant("alpha", owned);

		Map<DependencyAssistant, List<PsiFile>> grouped = SaveStateRefresh
				.groupByOwner(List.of(alpha), List.of(owned, orphan));

		assertThat(grouped).containsOnlyKeys(alpha);
		assertThat(grouped.get(alpha)).containsExactly(owned);
	}

	@Test
	void refreshRunsUpdaterOnceForSameAssistantBatch() {

		PsiFile fileOne = stubPsiFile();
		PsiFile fileTwo = stubPsiFile();
		FakeSourceAssistant alpha = new FakeSourceAssistant("alpha", fileOne, fileTwo);

		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		grouped.put(alpha, List.of(fileOne, fileTwo));

		refresh.refresh(grouped, indicator);

		assertThat(alpha.enumerateCalls).isEqualTo(1);
	}

	@Test
	void refreshRunsUpdaterOncePerRepresentedAssistant() {

		PsiFile alphaFile = stubPsiFile();
		PsiFile betaFile = stubPsiFile();
		FakeSourceAssistant alpha = new FakeSourceAssistant("alpha", alphaFile);
		FakeSourceAssistant beta = new FakeSourceAssistant("beta", betaFile);

		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		grouped.put(alpha, List.of(alphaFile));
		grouped.put(beta, List.of(betaFile));

		refresh.refresh(grouped, indicator);

		assertThat(alpha.enumerateCalls).isEqualTo(1);
		assertThat(beta.enumerateCalls).isEqualTo(1);
	}

	@Test
	void refreshSkipsWhenNoFilesAreGrouped() {

		FakeSourceAssistant alpha = new FakeSourceAssistant("alpha");

		refresh.refresh(new LinkedHashMap<>(), indicator);

		assertThat(alpha.enumerateCalls).isZero();
	}

	@Test
	void refreshFallsBackToFileScopedInvalidationForNonDependencySourceAssistant() {

		PsiFile owned = stubPsiFile();
		RecordingContext context = new RecordingContext(true);
		FakeNonSourceAssistant wrapper = new FakeNonSourceAssistant("wrapper", context);

		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		grouped.put(wrapper, List.of(owned));

		refresh.refresh(grouped, indicator);

		assertThat(context.invalidatedFiles).containsExactly(owned);
	}

	@Test
	void refreshSkipsInvalidationWhenNonDependencySourceContextIsUnavailable() {

		PsiFile owned = stubPsiFile();
		RecordingContext context = new RecordingContext(false);
		FakeNonSourceAssistant wrapper = new FakeNonSourceAssistant("wrapper", context);

		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		grouped.put(wrapper, List.of(owned));

		refresh.refresh(grouped, indicator);

		assertThat(context.invalidatedFiles).isEmpty();
	}

	@Test
	void fileScopedInvalidationEntryPointRemainsOnTheContext() throws Exception {

		// Compatibility guard: the public API used by direct callers must continue
		// to exist on ProjectDependencyContext.
		assertThat(ProjectDependencyContext.class.getMethod("invalidateState", PsiFile.class))
				.isNotNull();
	}

	// ------------------------------------------------------------------
	// Test doubles
	// ------------------------------------------------------------------

	private static PsiFile stubPsiFile() {
		return (PsiFile) Proxy.newProxyInstance(PsiFile.class.getClassLoader(),
				new Class<?>[] { PsiFile.class }, (proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return switch (method.getName()) {
							case "hashCode" -> System.identityHashCode(proxy);
							case "equals" -> proxy == args[0];
							case "toString" -> "PsiFile@" + System.identityHashCode(proxy);
							default -> null;
						};
					}
					return null;
				});
	}

	private static class FakeAssistant implements DependencyAssistant {

		private final String id;

		private final Set<PsiFile> ownedFiles;

		FakeAssistant(String id, PsiFile... ownedFiles) {
			this.id = id;
			this.ownedFiles = new HashSet<>(List.of(ownedFiles));
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getDisplayName() {
			return id;
		}

		@Override
		public boolean supports(Project project) {
			return true;
		}

		@Override
		public boolean supports(PsiFile file) {
			return ownedFiles.contains(file);
		}

		@Override
		public void initializeState(Project project, ProgressIndicator indicator) {
		}

		@Override
		public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
			return new DependencyCollector();
		}

		@Override
		public ProjectDependencyContext createContext(Project project, PsiFile anchor) {
			throw new UnsupportedOperationException();
		}

	}

	private static final class FakeSourceAssistant extends FakeAssistant implements DependencySource {

		private int enumerateCalls;

		FakeSourceAssistant(String id, PsiFile... ownedFiles) {
			super(id, ownedFiles);
		}

		@Override
		public List<DependencyScanEntry> enumerate(Project project) {
			enumerateCalls += 1;
			return List.of();
		}

		@Override
		public DependencyScanEntry createEntry(Project project, PsiFile file) {
			return null;
		}

		@Override
		public void collect(DependencyScanEntry entry, DependencyCollector collector) {
		}

	}

	private static final class FakeNonSourceAssistant extends FakeAssistant {

		private final ProjectDependencyContext context;

		FakeNonSourceAssistant(String id, ProjectDependencyContext context) {
			super(id);
			this.context = context;
		}

		@Override
		public ProjectDependencyContext createContext(Project project, PsiFile anchor) {
			return context;
		}

	}

	private static final class RecordingContext implements ProjectDependencyContext {

		private final boolean available;

		private final List<PsiFile> invalidatedFiles = new ArrayList<>();

		RecordingContext(boolean available) {
			this.available = available;
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public ProjectId getProjectId() {
			return ProjectId.of("fake", "fake");
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return List.of();
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void invalidateState(PsiFile file) {
			invalidatedFiles.add(file);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			return new DependencyCollector();
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
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
		}

	}

	private static final class TestProgressIndicator extends AbstractProgressIndicatorBase {

		TestProgressIndicator() {
			setIndeterminate(false);
		}

	}

}
