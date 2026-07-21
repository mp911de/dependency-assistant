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

package biz.paluch.dap.metadata;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link IssueTracker}.
 *
 * @author Mark Paluch
 */
class IssueTrackerUnitTests {

	@Test
	void parsesDeclaredUrl() {

		IssueTracker tracker = IssueTracker.parse("https://github.com/owner/repo/issues");

		assertThat(tracker).isNotNull();
		assertThat(tracker.getBaseUrl()).isEqualTo(URI.create("https://github.com/owner/repo/issues"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://example.com/my issues", "http://example.com/<issues>", "http://[malformed"})
	void yieldsNullForMalformedUrl(String url) {
		assertThat(IssueTracker.parse(url)).isNull();
	}

}
