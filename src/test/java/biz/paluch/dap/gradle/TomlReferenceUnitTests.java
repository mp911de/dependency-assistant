/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.gradle;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TomlReference}.
 * 
 * @author Mark Paluch
 */
class TomlReferenceUnitTests {

	@Test
	void libsFormatsAlias() {
		assertThat(TomlReference.libs("groovy-core").toString()).isEqualTo("libs.groovy.core");
	}

	@Test
	void pluginFormatsAlias() {
		assertThat(TomlReference.plugin("spring-boot")).hasToString("libs.plugins.spring.boot");
	}

	@Test
	void normalizesSeparators() {
		assertThat(TomlReference.libs("  spring--boot__starter.web  ").toString())
				.isEqualTo("libs.spring.boot.starter.web");
	}

	@Test
	void parsesLibraryAccessor() {
		assertThat(TomlReference.from("libs.spring-boot"))
				.isEqualTo(TomlReference.libs("spring.boot"));
	}

	@Test
	void parsesPluginAccessor() {
		assertThat(TomlReference.from("libs.plugins.spring-boot"))
				.isEqualTo(TomlReference.plugin("spring.boot"));
	}

	@Test
	void parsesLibrarySegments() {
		assertThat(TomlReference.from(List.of("libs", "spring", "boot")))
				.isEqualTo(TomlReference.libs("spring-boot"));
	}

	@Test
	void parsesPluginSegments() {
		assertThat(TomlReference.from(List.of("libs", "plugins", "spring", "boot")))
				.isEqualTo(TomlReference.plugin("spring-boot"));
	}

	@Test
	void roundTripsAccessor() {

		TomlReference reference = TomlReference.plugin("kotlin-jvm");
		TomlReference parsed = TomlReference.from(reference.toString());

		assertThat(parsed).isEqualTo(reference).hasSameHashCodeAs(reference);
	}

	@Test
	void returnsNullForBlank() {
		assertThat(TomlReference.from("  ")).isNull();
	}

	@Test
	void returnsNullForWrongRoot() {
		assertThat(TomlReference.from("tools.spring.boot")).isNull();
	}

	@Test
	void returnsNullForRootOnly() {
		assertThat(TomlReference.from("libs")).isNull();
	}

	@Test
	void returnsNullForPluginRootOnly() {
		assertThat(TomlReference.from("libs.plugins")).isNull();
	}

	@Test
	void returnsNullForReservedSections() {
		assertThat(TomlReference.from("libs.versions.junit")).isNull();
		assertThat(TomlReference.from("libs.bundles.test")).isNull();
	}

	@Test
	void rejectsNullInFactories() {
		assertThatIllegalArgumentException().isThrownBy(() -> TomlReference.libs(null));
		assertThatIllegalArgumentException().isThrownBy(() -> TomlReference.plugin(null));
		assertThatIllegalArgumentException().isThrownBy(() -> TomlReference.from((String) null));
	}

}
