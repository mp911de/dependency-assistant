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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.ticket.TicketKey;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubTicketSystem}.
 *
 * @author Mark Paluch
 */
class GitHubTicketSystemUnitTests {

	private static final GitRepositoryMetadata COORDINATES = new GitRepositoryMetadata("github.com", "mp911de",
			"dependency-assistant");

	GitHubTicketRepository repository = new GitHubTicketRepository(GithubServerPath.DEFAULT_SERVER, COORDINATES,
			GithubApiRequestExecutor.Factory.getInstance().create(), new GitHubTicketCache());

	GitHubTicketSystem system = new GitHubTicketSystem(repository);

	TicketKey TICKET_KEY = TicketKey.of("1234");

	@Test
	void rendersDisplayReference() {
		assertThat(system.getDisplayReference(TICKET_KEY)).isEqualTo("#1234");
	}

	@Test
	void rendersCloseReference() {
		assertThat(system.getCloseReference(TICKET_KEY)).isEqualTo("Closes #1234");
	}

}
