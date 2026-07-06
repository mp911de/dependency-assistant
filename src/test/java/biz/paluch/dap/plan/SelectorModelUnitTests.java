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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SelectorAction.SelectorModel}.
 *
 * @author Mark Paluch
 */
class SelectorModelUnitTests {

	@Test
	void catalogRebindAppliesSelectionWithoutPublishingAUserSelection() {

		TestSelectorModel model = new TestSelectorModel();
		TestOption persisted = new TestOption("persisted");

		model.setValues(List.of(persisted, new TestOption("other")), persisted);

		assertThat(model.getSelected()).isEqualTo(persisted);
		assertThat(model.selections).isEmpty();
	}

	@Test
	void catalogRebindWithoutSelectionClearsThePreviousPick() {

		TestSelectorModel model = new TestSelectorModel();
		TestOption selected = new TestOption("selected");
		model.setValues(List.of(selected), null);
		model.setSelectedItem(selected);

		model.setValues(List.of(new TestOption("other")), null);

		assertThat(model.getSelected()).isNull();
		assertThat(model.selections).containsExactly(selected);
	}

	@Test
	void userSelectionIsPublished() {

		TestSelectorModel model = new TestSelectorModel();
		TestOption selected = new TestOption("selected");
		model.setValues(List.of(selected), null);

		model.setSelectedItem(selected);

		assertThat(model.selections).containsExactly(selected);
	}

	private static class TestSelectorModel extends SelectorAction.SelectorModel<TestOption> {

		private final List<@Nullable TestOption> selections = new ArrayList<>();

		@Override
		String getText(TestOption value) {
			return value.name();
		}

		@Override
		void selectionChanged(@Nullable TestOption selection) {
			selections.add(selection);
		}

	}

	private static class TestOption {

		private final String name;

		TestOption(String name) {
			this.name = name;
		}

		String name() {
			return name;
		}

	}

}
