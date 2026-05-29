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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ProjectStateUpdater}.
 *
 * @author Mark Paluch
 */
class ProjectStateUpdaterUnitTests {

	private final StateService service = new StateService();

	private final ProjectStateUpdater updater = new ProjectStateUpdater(null, service);

	@Test
	void updateAllRunsEnumerateAndCollectForEachAvailableEntry() {

		FakeContext alpha = FakeContext.available("alpha");
		FakeContext beta = FakeContext.available("beta");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha),
				DependencyScanEntry.of(null, beta));

		updater.updateAll(source, new TestProgressIndicator());

		assertThat(source.enumerateCalls).isEqualTo(1);
		assertThat(source.collectedEntries).hasSize(2);
	}

	@Test
	void updateAllSkipsUnavailableContexts() {

		FakeContext available = FakeContext.available("ready");
		FakeContext unavailable = FakeContext.unavailable();
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, available),
				DependencyScanEntry.of(null, unavailable));

		updater.updateAll(source, new TestProgressIndicator());

		assertThat(source.collectedEntries).hasSize(1);
		assertThat(source.collectedEntries.get(0).context()).isSameAs(available);
		assertThat(service.getProjectState(unavailable.getProjectId()).hasDependencies()).isFalse();
		assertThat(service.getProjectState(available.getProjectId()).hasDependencies()).isTrue();
	}

	@Test
	void updateAllPassesPhaseOneCollectorsThroughCompletionAndStore() {

		FakeContext context = FakeContext.available("module");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, context));
		RecordingStateService recording = new RecordingStateService();
		ProjectStateUpdater recordingUpdater = new ProjectStateUpdater(null, recording);

		recordingUpdater.updateAll(source, new TestProgressIndicator());

		assertThat(source.collectedCollectors).hasSize(1);
		assertThat(source.introspected.completedCollectors).hasSize(1);
		assertThat(source.introspected.completedCollectors.get(0))
				.isSameAs(source.collectedCollectors.get(0));
		assertThat(recording.storedFor(context.getProjectId()))
				.isSameAs(source.collectedCollectors.get(0));
	}

	@Test
	void updateAllInvalidatesBeforeStore() {

		FakeContext context = FakeContext.available("module");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, context));
		RecordingStateService recording = new RecordingStateService();
		ProjectStateUpdater recordingUpdater = new ProjectStateUpdater(null, recording);

		recordingUpdater.updateAll(source, new TestProgressIndicator());

		List<String> events = recording.events(context.getProjectId());
		assertThat(events).containsExactly("invalidate", "store");
	}

	@Test
	void updateAllCompletesAllCollectorsBeforeAnyStore() {

		FakeContext alpha = FakeContext.available("alpha");
		FakeContext beta = FakeContext.available("beta");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha),
				DependencyScanEntry.of(null, beta));
		RecordingStateService recording = new RecordingStateService();
		source.introspected.events = recording.eventLog;
		ProjectStateUpdater recordingUpdater = new ProjectStateUpdater(null, recording);

		recordingUpdater.updateAll(source, new TestProgressIndicator());

		assertThat(recording.eventLog).containsExactly("complete", "complete", "invalidate", "store",
				"invalidate", "store", "updateCache");
	}

	@Test
	void updateAllUpdatesCacheOnlyAfterStoresHaveRun() {

		FakeContext context = FakeContext.available("module");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, context));
		RecordingStateService recording = new RecordingStateService();
		source.introspected.events = recording.eventLog;
		ProjectStateUpdater recordingUpdater = new ProjectStateUpdater(null, recording);

		recordingUpdater.updateAll(source, new TestProgressIndicator());

		assertThat(recording.eventLog).containsExactly("complete", "invalidate", "store", "updateCache");
	}

	@Test
	void updateAllReportsProgress() {

		FakeContext alpha = FakeContext.available("alpha");
		FakeContext beta = FakeContext.available("beta");
		FakeContext gamma = FakeContext.available("gamma");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha),
				DependencyScanEntry.of(null, beta), DependencyScanEntry.of(null, gamma));
		RecordingProgressIndicator indicator = new RecordingProgressIndicator();

		updater.updateAll(source, indicator);

		assertThat(indicator.fractions).containsExactly(1.0 / 3.0, 2.0 / 3.0, 1.0);
	}

	@Test
	void updateAllPropagatesCancellation() {

		FakeContext alpha = FakeContext.available("alpha");
		FakeContext beta = FakeContext.available("beta");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha),
				DependencyScanEntry.of(null, beta));
		TestProgressIndicator indicator = new TestProgressIndicator();
		indicator.cancel();

		ThrowingCallable call = () -> updater.updateAll(source, indicator);

		assertThatThrownBy(call).isInstanceOf(ProcessCanceledException.class);
		assertThat(source.collectedEntries).isEmpty();
	}

	@Test
	void aggregateAssemblesReleaseSourcesFromAvailableContexts() {

		ReleaseSource alphaSource = (id, ind) -> List.of();
		ReleaseSource sharedSource = (id, ind) -> List.of();
		FakeContext alpha = FakeContext.available("alpha", alphaSource, sharedSource);
		FakeContext beta = FakeContext.available("beta", sharedSource);
		FakeContext skipped = FakeContext.unavailable();
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha),
				DependencyScanEntry.of(null, beta), DependencyScanEntry.of(null, skipped));

		DependencyCollector aggregate = updater.aggregate(source, new TestProgressIndicator());

		assertThat(aggregate.getReleaseSources()).containsExactlyInAnyOrder(alphaSource, sharedSource);
	}

	@Test
	void aggregateRunsCompletionOnAggregateCollector() {

		FakeContext alpha = FakeContext.available("alpha");
		FakeSource source = new FakeSource(DependencyScanEntry.of(null, alpha));

		DependencyCollector aggregate = updater.aggregate(source, new TestProgressIndicator());

		assertThat(source.introspected.completedCollectors).containsExactly(aggregate);
	}

	@Test
	void invalidateFileRoutesThroughCompleteStoreAndUpdateCache() {

		FakeContext context = FakeContext.available("module");
		FakeSource source = new FakeSource();
		source.entryForFile = DependencyScanEntry.of(null, context);
		RecordingStateService recording = new RecordingStateService();
		source.introspected.events = recording.eventLog;
		ProjectStateUpdater recordingUpdater = new ProjectStateUpdater(null, recording);

		recordingUpdater.invalidateFile(source, null);

		assertThat(recording.eventLog).containsExactly("complete", "invalidate", "store", "updateCache");
	}

	@Test
	void invalidateFileSkipsWhenSourceReturnsNoEntry() {

		FakeSource source = new FakeSource();
		source.entryForFile = null;

		updater.invalidateFile(source, null);

		assertThat(source.collectedCollectors).isEmpty();
		assertThat(source.introspected.completedCollectors).isEmpty();
	}

	@Test
	void invalidateFileSkipsWhenContextIsUnavailable() {

		FakeContext unavailable = FakeContext.unavailable();
		FakeSource source = new FakeSource();
		source.entryForFile = DependencyScanEntry.of(null, unavailable);

		updater.invalidateFile(source, null);

		assertThat(source.collectedCollectors).isEmpty();
		assertThat(source.introspected.completedCollectors).isEmpty();
	}

	// ------------------------------------------------------------------
	// Test doubles
	// ------------------------------------------------------------------

	private static final class FakeSource implements DependencySource {

		private final List<DependencyScanEntry> entries = new ArrayList<>();

		private final List<DependencyScanEntry> collectedEntries = new ArrayList<>();

		private final List<DependencyCollector> collectedCollectors = new ArrayList<>();

		private final RecordingIntrospected introspected = new RecordingIntrospected();

		private int enumerateCalls;

		private DependencyScanEntry entryForFile;

		FakeSource(DependencyScanEntry... initial) {
			for (DependencyScanEntry entry : initial) {
				entries.add(entry);
			}
		}

		@Override
		public List<DependencyScanEntry> enumerate(Project project) {
			enumerateCalls += 1;
			return entries;
		}

		@Override
		public DependencyScanEntry createEntry(Project project, PsiFile file) {
			return entryForFile;
		}

		@Override
		public void collect(DependencyScanEntry entry, DependencyCollector collector) {
			collectedEntries.add(entry);
			collectedCollectors.add(collector);
			ArtifactId artifactId = ArtifactId.of("group", entry.context().getProjectId().artifactId());
			collector.registerUsage(artifactId, ArtifactVersion.of("1.0"), DeclarationSource.dependency(),
					VersionSource.declared("1.0"));
		}

		@Override
		public IntrospectedDependencies introspect(Project project) {
			return introspected;
		}

	}

	private static final class RecordingIntrospected implements IntrospectedDependencies {

		private final List<DependencyCollector> completedCollectors = new ArrayList<>();

		private List<String> events;

		@Override
		public void complete(DependencyCollector collector) {
			completedCollectors.add(collector);
			if (events != null) {
				events.add("complete");
			}
		}

		@Override
		public void updateCache(Cache cache) {
			if (events != null) {
				events.add("updateCache");
			}
		}

	}

	private static final class FakeContext implements ProjectBuildContext {

		private final boolean available;

		private final ProjectId projectId;

		private final List<ReleaseSource> releaseSources;

		private FakeContext(boolean available, ProjectId projectId, List<ReleaseSource> releaseSources) {
			this.available = available;
			this.projectId = projectId;
			this.releaseSources = releaseSources;
		}

		static FakeContext available(String name, ReleaseSource... sources) {
			return new FakeContext(true, ProjectId.of("fake", name), List.of(sources));
		}

		static FakeContext unavailable() {
			return new FakeContext(false, ProjectId.of("fake", "absent"), List.of());
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return releaseSources;
		}

	}

	private static class TestProgressIndicator extends AbstractProgressIndicatorBase {

		TestProgressIndicator() {
			setIndeterminate(false);
		}

		@Override
		public void checkCanceled() {
			if (isCanceled()) {
				throw new ProcessCanceledException();
			}
		}

	}

	private static final class RecordingProgressIndicator extends TestProgressIndicator {

		private final List<Double> fractions = new ArrayList<>();

		@Override
		public void setFraction(double fraction) {
			super.setFraction(fraction);
			fractions.add(fraction);
		}

	}

	private static final class RecordingStateService extends StateService {

		private final Map<ProjectId, RecordingProjectState> states = new HashMap<>();

		private final List<String> eventLog = new ArrayList<>();

		@Override
		public ProjectState getProjectState(ProjectId identity) {
			return states.computeIfAbsent(identity, id -> new RecordingProjectState(eventLog));
		}

		List<String> events(ProjectId identity) {
			RecordingProjectState state = states.get(identity);
			return state == null ? List.of() : List.copyOf(state.events);
		}

		DependencyCollector storedFor(ProjectId identity) {
			RecordingProjectState state = states.get(identity);
			return state == null ? null : state.stored;
		}

	}

	private static final class RecordingProjectState implements ProjectState {

		private final List<String> ownerLog;

		private final List<String> events = new ArrayList<>();

		private DependencyCollector stored;

		RecordingProjectState(List<String> ownerLog) {
			this.ownerLog = ownerLog;
		}

		@Override
		public Dependency findDependency(ArtifactId artifactId) {
			return stored == null ? null : stored.getUsage(artifactId);
		}

		@Override
		public void setDependencies(DependencyCollector collector) {
			this.stored = collector;
			events.add("store");
			ownerLog.add("store");
		}

		@Override
		public boolean hasDependencies() {
			return stored != null;
		}

		@Override
		public void invalidateDependencies() {
			this.stored = null;
			events.add("invalidate");
			ownerLog.add("invalidate");
		}

		@Override
		public VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

		@Override
		public ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

	}

}
