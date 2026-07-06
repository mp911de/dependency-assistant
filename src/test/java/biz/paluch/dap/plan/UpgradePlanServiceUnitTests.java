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

package biz.paluch.dap.plan;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpdateCandidate;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Plan;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketQuery;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSpec;
import biz.paluch.dap.ticket.TicketState;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanService} command history.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradePlanServiceUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("1.0.0");

	private static final ArtifactVersion TARGET = ArtifactVersion.of("1.1.0");

	@Test
	void repeatedDeletesUndoAndRedoInSequence(Project project) {

		List<UpgradePlanItem> items = materializedItems(project, "alpha", "bravo");
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UndoManager undoManager = UndoManager.getInstance(project);
		try {
			ApplicationManager.getApplication().invokeAndWait(() -> {
				service.removeItems(List.of(items.get(0)));
				service.removeItems(List.of(items.get(1)));
			});
			assertThat(service.getUpgradePlan().isEmpty()).isTrue();

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.undo(null));
			assertThat(service.getUpgradePlan().getItems()).containsExactly(items.get(1));

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.undo(null));
			assertThat(service.getUpgradePlan().getItems()).containsExactlyElementsOf(items);

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.redo(null));
			assertThat(service.getUpgradePlan().getItems()).containsExactly(items.get(1));

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.redo(null));
			assertThat(service.getUpgradePlan().isEmpty()).isTrue();
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
		}
	}

	@Test
	void linkingATicketIsUndoable(Project project) {

		Disposable extension = Disposer.newDisposable();
		TicketRepository repository = new TestTicketRepository();
		TicketKey key = TicketKey.of("42");
		TicketSystem ticketSystem = new TestTicketSystem(repository);
		TicketSystemProvider provider = new TicketSystemProvider() {

			@Override
			public boolean supports(Project candidate) {
				return candidate == project;
			}

			@Override
			public TicketSystem create(Project candidate) {
				return ticketSystem;
			}

		};
		TicketSystemProvider.EP_NAME.getPoint().registerExtension(provider, extension);

		UndoManager undoManager = UndoManager.getInstance(project);
		try {
			UpgradePlanItem item = materializedItems(project, "alpha").getFirst();
			UpgradePlanService service = UpgradePlanService.getInstance(project);
			Ticket ticket = new TestTicket(key);

			ApplicationManager.getApplication().invokeAndWait(() -> service.linkTicket(item, repository, ticket));
			assertThat(item.getTicketKey()).isEqualTo(key);

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.undo(null));
			assertThat(item.getTicketKey()).isNull();

			ApplicationManager.getApplication().invokeAndWait(() -> undoManager.redo(null));
			assertThat(item.getTicketKey()).isEqualTo(key);
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
			Disposer.dispose(extension);
		}
	}

	@Test
	@ProjectFile(name = "pom.xml", content = "before")
	void nonUndoableApplyBoundaryPreventsEarlierCommands(Project project, PsiFile file) {

		UpgradePlanItem item = materializedItems(project, "alpha").getFirst();
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UndoManager undoManager = UndoManager.getInstance(project);
		try {
			ApplicationManager.getApplication().invokeAndWait(() -> service.removeItem(item));
			assertThat(undoManager.isUndoAvailable(null)).isTrue();

			ApplicationManager.getApplication().invokeAndWait(
					() -> service.vcsApplied(FileScope.of(file.getVirtualFile())));

			assertThatExceptionOfType(RuntimeException.class)
					.isThrownBy(() -> ApplicationManager.getApplication().invokeAndWait(() -> undoManager.undo(null)))
					.withMessageContaining("cannot be undone");
			assertThat(service.getUpgradePlan().isEmpty()).isTrue();
			assertThat(undoManager.isRedoAvailable(null)).isFalse();
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
		}
	}

	private static List<UpgradePlanItem> materializedItems(Project project, String... names) {

		Content content = new Content();
		content.getAffectedFiles().add("pom.xml");
		List<UpgradePlanItem> items = new java.util.ArrayList<>();
		for (String name : names) {
			UpgradeCandidate candidate = candidate(name);
			Item stored = Item.from(candidate, TARGET);
			UpgradePlanItem item = new UpgradePlanItem(stored.getId(), candidate, TARGET);
			stored.setMaterialized(item);
			content.getItems().add(stored);
			items.add(item);
		}
		Plan persisted = new Plan();
		persisted.setContent(content);
		UpgradePlanState.getInstance(project).loadState(persisted);
		((UndoManagerImpl) UndoManager.getInstance(project)).dropHistoryInTests();
		return items;
	}

	private static UpgradeCandidate candidate(String name) {

		Dependency dependency = new Dependency(ArtifactId.of("org.example", name), CURRENT);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(CURRENT.toString()));
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(
				Map.of(CURRENT, Vulnerabilities.clean(), TARGET, Vulnerabilities.clean()));
		return new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, Releases.just(Release.of(TARGET)), vulnerabilities),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.of(CURRENT));
	}

	private static class TestTicketSystem implements TicketSystem {

		private final TicketRepository repository;

		TestTicketSystem(TicketRepository repository) {
			this.repository = repository;
		}

		@Override
		public TicketRepository getRepository() {
			return repository;
		}

		@Override
		public String getDisplayReference(TicketKey key) {
			return "#" + key;
		}

		@Override
		public String getCloseReference(TicketKey key) {
			return "Closes #" + key;
		}

	}

	private static class TestTicketRepository implements TicketRepository {

		@Override
		public List<? extends Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query) {
			return List.of();
		}

		@Override
		public Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec)
				throws IOException {
			throw new IOException("Not supported");
		}

		@Override
		public List<? extends TicketState> getTicketStates(ProgressIndicator indicator) {
			return List.of();
		}

		@Override
		public List<? extends Milestone> getMilestones(ProgressIndicator indicator) {
			return List.of();
		}

		@Override
		public List<? extends Label> getLabels(ProgressIndicator indicator) {
			return List.of();
		}

		@Override
		public TicketRepository cached() {
			return this;
		}

	}

	private static class TestTicket implements Ticket {

		private final TicketKey key;

		TestTicket(TicketKey key) {
			this.key = key;
		}

		@Override
		public TicketKey getKey() {
			return key;
		}

		@Override
		public String getTitle() {
			return "Upgrade";
		}

		@Override
		public TicketState getState() {
			return () -> true;
		}

		@Override
		public URI getWebLink() {
			return URI.create("https://example.test/tickets/42");
		}

		@Override
		public List<Milestone> getMilestones() {
			return List.of();
		}

		@Override
		public List<Label> getLabels() {
			return List.of();
		}

	}

}
