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

package biz.paluch.dap.state;

import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.fixtures.Coordinates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ApplicationSettings}.
 *
 * @author Mark Paluch
 */
class ApplicationSettingsUnitTests {

	ApplicationSettings settings = new ApplicationSettings();

	PackageIdentity junit = Coordinates.of("junit:foo:1").getPackageIdentity();

	PackageIdentity spring = Coordinates.of("spring:bar:1").getPackageIdentity();

	PackageIdentity intellij = Coordinates.of("intellij:bar:1").getPackageIdentity();

	@Test
	void shouldAllowDuplicates() {

		settings.addNameHint("Junit", junit);
		settings.addNameHint("Junit 6", junit);

		settings.doWithState(state -> {
			return assertThat(state.getNameHints()).hasSize(2);
		});
	}

	@Test
	void shouldRemoveDuplicatesOnSnapshot() {

		settings.addNameHint("Junit", junit);
		settings.addNameHint("Spring", spring);
		settings.addNameHint("Junit 6", junit);
		settings.addNameHint("intellij", intellij);

		ApplicationSettings.State state = settings.getState();

		assertThat(state.getNameHints()).hasSize(3)
				.extracting(ApplicationSettings.NameHint::getName)
				.containsSequence("Spring", "Junit 6", "intellij");
	}

}
