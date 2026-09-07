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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.assistant.AssistantTemplateGroup;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.ticket.TicketKey;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GitHubTicketSystem} reference rendering.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubTicketSystemUnitTests {

	private static final GitRepositoryMetadata COORDINATES = new GitRepositoryMetadata("github.com", "mp911de",
			"dependency-assistant");

	private static final TicketKey TICKET_KEY = TicketKey.of("1234");

	@TestFixture
	Project project;

	GitHubTicketSystem system;

	FileTemplate closeTemplate;

	String defaultCloseTemplate;

	@BeforeEach
	void setUp() {

		GitHubTicketRepository repository = new GitHubTicketRepository(GithubServerPath.DEFAULT_SERVER, COORDINATES,
				GithubApiRequestExecutor.Factory.getInstance().create(), new GitHubTicketCache(), project);
		system = new GitHubTicketSystem(repository);

		closeTemplate = FileTemplateManager.getInstance(project)
				.getJ2eeTemplate(AssistantTemplateGroup.GITHUB_CLOSE_REFERENCE_TEMPLATE);
		defaultCloseTemplate = closeTemplate.getText();
	}

	@AfterEach
	void tearDown() {
		closeTemplate.setText(defaultCloseTemplate);
	}

	@Test
	void rendersDisplayReference() {
		assertThat(system.getDisplayReference(TICKET_KEY)).isEqualTo("#1234");
	}

	@Test
	void rendersDefaultCloseReference() {
		assertThat(system.getCloseReference(TICKET_KEY)).isEqualTo("Closes #1234");
	}

	@Test
	void rendersCustomizedCloseReference() {

		closeTemplate.setText("[resolves GH-${NUMBER}]");
		assertThat(system.getCloseReference(TICKET_KEY)).isEqualTo("[resolves GH-1234]");

		closeTemplate.setText("Fixes ${FULL_REFERENCE} in ${OWNER}/${REPOSITORY}");
		assertThat(system.getCloseReference(TICKET_KEY))
				.isEqualTo("Fixes mp911de/dependency-assistant#1234 in mp911de/dependency-assistant");
	}

}
