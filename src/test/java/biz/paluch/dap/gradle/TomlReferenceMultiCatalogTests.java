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
package biz.paluch.dap.gradle;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for multi-catalog support in {@link TomlReference}.
 *
 * @author Mark Paluch
 */
class TomlReferenceMultiCatalogTests {

	@Test
	void backwardCompatSingleArgDelegatesToLibs() {
		assertThat(TomlReference.from(List.of("libs", "spring", "boot")))
				.isEqualTo(TomlReference.libs("spring.boot"));
	}

	@Test
	void twoArgOverloadAcceptsKnownAlias() {
		TomlReference ref = TomlReference.from(List.of("tools", "checkstyle"), Set.of("tools"));

		assertThat(ref).isNotNull();
		assertThat(ref.getCatalogAlias()).isEqualTo("tools");
		assertThat(ref.getTableName()).isEqualTo(TomlParser.LIBRARIES);
		assertThat(ref.toString()).isEqualTo("tools.checkstyle");
	}

	@Test
	void twoArgOverloadRejectsUnknownAlias() {
		assertThat(TomlReference.from(List.of("custom", "spring"), Set.of("libs", "tools"))).isNull();
	}

	@Test
	void twoArgOverloadAcceptsPluginSection() {
		TomlReference ref = TomlReference.from(List.of("tools", "plugins", "my", "plugin"), Set.of("tools"));

		assertThat(ref).isNotNull();
		assertThat(ref.getCatalogAlias()).isEqualTo("tools");
		assertThat(ref.getTableName()).isEqualTo(TomlParser.PLUGINS);
	}

	@Test
	void twoArgOverloadRequiresMinimumSize() {
		assertThat(TomlReference.from(List.of("tools"), Set.of("tools"))).isNull();
	}

	@Test
	void twoArgOverloadSkipsReservedSections() {
		assertThat(TomlReference.from(List.of("tools", "versions", "spring"), Set.of("tools"))).isNull();
		assertThat(TomlReference.from(List.of("tools", "bundles", "test"), Set.of("tools"))).isNull();
	}

	@Test
	void ofFactoryCreatesReference() {
		TomlReference ref = TomlReference.of("tools", null, "spring-boot");

		assertThat(ref.getCatalogAlias()).isEqualTo("tools");
		assertThat(ref.getTableName()).isEqualTo(TomlParser.LIBRARIES);
		assertThat(ref.toString()).isEqualTo("tools.spring.boot");
	}

	@Test
	void getCatalogAliasReturnsLibsForDefault() {
		assertThat(TomlReference.libs("spring-boot").getCatalogAlias()).isEqualTo("libs");
	}

	@Test
	void getTableNameAlwaysReturnsLibrariesForNullSection() {
		TomlReference ref = TomlReference.of("tools", null, "my-lib");
		assertThat(ref.getTableName()).isEqualTo(TomlParser.LIBRARIES);
	}

}
