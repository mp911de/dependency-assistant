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

package biz.paluch.dap.artifact;

import java.util.List;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersionCaretRemap}.
 *
 * @author Mark Paluch
 */
class VersionCaretRemapUnitTests {

	@Test
	void emptyRemapCannotTranslate() {
		assertThat(VersionCaretRemap.none().canTranslate()).isFalse();
	}

	@Test
	void singleRangeTranslatesToNewEndOffset() {

		VersionCaretRemap remap = VersionCaretRemap.of(List.of(new TextRange(10, 15)), List.of(new TextRange(10, 17)));

		assertThat(remap.canTranslate()).isTrue();
		assertThat(remap.translate(12)).isEqualTo(17);
	}

	@Test
	void multiRangePicksOccurrenceUnderCaret() {

		VersionCaretRemap remap = VersionCaretRemap.of(List.of(new TextRange(10, 15), new TextRange(40, 45)),
				List.of(new TextRange(10, 17), new TextRange(42, 49)));

		assertThat(remap.translate(42)).isEqualTo(49);
	}

	@Test
	void multiRangeFallsBackToFirstWhenCaretOutsideEveryOccurrence() {

		VersionCaretRemap remap = VersionCaretRemap.of(List.of(new TextRange(10, 15), new TextRange(40, 45)),
				List.of(new TextRange(10, 17), new TextRange(42, 49)));

		assertThat(remap.translate(0)).isEqualTo(17);
	}

}
