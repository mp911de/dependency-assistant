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

package biz.paluch.dap.npm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmVersionExpressionParser}.
 *
 * @author Mark Paluch
 */
class NpmVersionParserTests {

	@Test
	void parsesPlainSemver() {
		NpmVersionExpression expression = NpmVersionExpressionParser.parse("1.6.8");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Exact("", "1.6.8"));
	}

	@Test
	void parsesCaretRange() {
		assertThat(NpmVersionExpressionParser.parse("^3.1.2"))
				.isEqualTo(new NpmVersionExpression.Exact("^", "3.1.2"));
	}

	@Test
	void parsesTildeRange() {
		assertThat(NpmVersionExpressionParser.parse("~1.2.3"))
				.isEqualTo(new NpmVersionExpression.Exact("~", "1.2.3"));
	}

	@Test
	void parsesEqualsExact() {
		assertThat(NpmVersionExpressionParser.parse("=1.0.0"))
				.isEqualTo(new NpmVersionExpression.Exact("=", "1.0.0"));
	}

	@Test
	void parsesVPrefixedVersion() {
		assertThat(NpmVersionExpressionParser.parse("v2.0.0-beta.1"))
				.isEqualTo(new NpmVersionExpression.Exact("v", "2.0.0-beta.1"));
	}

	@Test
	void parsesPrefixRange() {
		assertThat(NpmVersionExpressionParser.parse("2.x"))
				.isEqualTo(new NpmVersionExpression.Prefix("2.x"));
		assertThat(NpmVersionExpressionParser.parse("3.4.x"))
				.isEqualTo(new NpmVersionExpression.Prefix("3.4.x"));
		assertThat(NpmVersionExpressionParser.parse("1.*"))
				.isEqualTo(new NpmVersionExpression.Prefix("1.*"));
	}

	@Test
	void parsesHyphenRange() {
		NpmVersionExpression expression = NpmVersionExpressionParser.parse("1.0.0 - 2.9999.9999");
		assertThat(expression).isEqualTo(new NpmVersionExpression.RangeUpper("1.0.0 - ", "2.9999.9999"));
	}

	@Test
	void parsesComparatorPair() {
		NpmVersionExpression expression = NpmVersionExpressionParser.parse(">=1.0.2 <2.1.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.RangeUpper(">=1.0.2 <", "2.1.2"));
	}

	@Test
	void parsesComparatorPairWithLessThanOrEqualUpper() {
		NpmVersionExpression expression = NpmVersionExpressionParser.parse(">=1.0.2 <=2.1.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.RangeUpper(">=1.0.2 <=", "2.1.2"));
	}

	@Test
	void parsesAlias() {
		NpmVersionExpression expression = NpmVersionExpressionParser.parse("npm:@ankurk91/bootstrap-vue@^3.0.2");
		NpmVersionExpression.Exact inner = new NpmVersionExpression.Exact("^", "3.0.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Alias("@ankurk91/bootstrap-vue", inner));
	}

	@Test
	void rejectsAliasWithoutInnerVersion() {
		assertThat(NpmVersionExpressionParser.parse("npm:@scope/name@")).isNull();
		assertThat(NpmVersionExpressionParser.parse("npm:@scope/name")).isNull();
	}

	@Test
	void rejectsNestedAlias() {
		assertThat(NpmVersionExpressionParser.parse("npm:a@npm:b@1.0.0")).isNull();
	}

	@Test
	void rejectsRangeUpperWithPrefixUpper() {
		assertThat(NpmVersionExpressionParser.parse(">=1.0.0 <2.x")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "*", "latest", "LATEST", "<1.0.0 || >=2.3.1"})
	void rejectsOutOfScopeShapes(String input) {
		assertThat(NpmVersionExpressionParser.parse(input)).isNull();
	}

	@Test
	void rejectsNullInput() {
		assertThat(NpmVersionExpressionParser.parse(null)).isNull();
	}

	@Test
	void rejectsClassifierSuffixedVersion() {
		assertThat(NpmVersionExpressionParser.parse("1.6.8.RELEASE")).isNull();
	}

}
