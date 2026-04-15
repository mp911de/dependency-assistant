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
package biz.paluch.dap.support;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PropertyResolverUtil}.
 * 
 * @author Mark Paluch
 */
class PropertyResolverUtilUnitTests {

	@Test
	void resolveInterpolated_singlePlaceholder() {
		assertThat(
				PropertyResolverUtil.resolveInterpolated("${a}", wrap(Map.of("a", "org.foo"))))
						.isEqualTo("org.foo");
	}

	@Test
	void resolveInterpolated_mixedString() {
		assertThat(
				PropertyResolverUtil.resolveInterpolated("com.${a}", wrap(Map.of("a", "foo"))))
						.isEqualTo("com.foo");
	}

	@Test
	void resolveInterpolated_unbracedPlaceholder() {
		assertThat(
				PropertyResolverUtil.resolveInterpolated("$a", wrap(Map.of("a", "org.foo"))))
						.isEqualTo("org.foo");
	}

	@Test
	void resolveInterpolated_mixedBracedAndUnbraced() {
		assertThat(PropertyResolverUtil.resolveInterpolated("$a.${b}",
				wrap(Map.of("a", "com", "b", "foo")))).isEqualTo("com.foo");
	}

	@Test
	void resolveInterpolated_unknownPlaceholderLeftInPlace() {
		assertThat(PropertyResolverUtil.resolveInterpolated("${missing}", wrap(Map.of())))
				.isEqualTo("${missing}");
	}

	@Test
	void resolveInterpolated_unbracedUnknownLeftInPlace() {
		assertThat(PropertyResolverUtil.resolveInterpolated("$missing", wrap(Map.of())))
				.isEqualTo("$missing");
	}

	@Test
	void resolveInterpolated_emptyValueLeftAsEmpty() {
		assertThat(PropertyResolverUtil.resolveInterpolated("${a}", wrap(Map.of("a", ""))))
				.isEmpty();
	}

	@Test
	void resolveChained_twoHops() {
		Map<String, String> props = Map.of("a", "${b}", "b", "org.foo");
		assertThat(PropertyResolverUtil.resolvePlaceholders("${a}", wrap(props)))
				.isEqualTo("org.foo");
	}

	@Test
	void resolveChained_threeHops() {
		Map<String, String> props = Map.of("a", "${b}", "b", "${c}", "c", "org.foo");
		assertThat(PropertyResolverUtil.resolvePlaceholders("${a}", wrap(props)))
				.isEqualTo("org.foo");
	}

	@Test
	void resolveChained_cycle() {
		Map<String, String> props = Map.of("a", "${b}", "b", "${a}");
		String result = PropertyResolverUtil.resolvePlaceholders("${a}", wrap(props));
		assertThat(PropertyResolverUtil.hasUnresolvedPlaceholder(result)).isTrue();
	}

	@Test
	void resolveChained_missingSecondHop() {
		Map<String, String> props = Map.of("a", "${b}");
		assertThat(PropertyResolverUtil.resolvePlaceholders("${a}", wrap(props))).isEqualTo("${b}");
	}

	@Test
	void resolveChained_depthCapReached() {
		Map<String, String> props = new LinkedHashMap<>();
		props.put("p12", "org.foo");
		for (int i = 11; i >= 1; i--) {
			props.put("p" + i, "${p" + (i + 1) + "}");
		}
		String result = PropertyResolverUtil.resolvePlaceholders("${p1}", wrap(props));
		assertThat(PropertyResolverUtil.hasUnresolvedPlaceholder(result)).isTrue();
	}

	/**
	 * Returns a {@link PropertyResolver} backed by a fixed snapshot of
	 * {@code properties} (e.g. tests or parser seed maps).
	 */
	public static PropertyResolver wrap(Map<String, String> properties) {

		Map<String, String> frozen = Map.copyOf(properties);
		return frozen::get;
	}

}
