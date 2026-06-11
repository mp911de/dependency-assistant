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

package biz.paluch.dap.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Expression}.
 *
 * @author Mark Paluch
 */
class ExpressionUnitTests {

	@ParameterizedTest(name = "{0} resolves property {1}")
	@CsvSource({"${springVersion}, springVersion", "$springVersion, springVersion",
			"${spring.version}, spring.version"})
	void propertyExpressionIsRecognized(String value, String propertyName) {

		Expression expression = Expression.from(value);

		assertThat(expression.isProperty()).isTrue();
		assertThat(expression.getPropertyName()).isEqualTo(propertyName);
	}

	@ParameterizedTest(name = "{0} is a literal")
	@ValueSource(strings = {"6.2.0", "$1abc", "prefix$springVersion", "$spring.version", "${a}-${b}"})
	void nonPropertyValueIsLiteral(String value) {
		assertThat(Expression.from(value).isProperty()).isFalse();
	}

}
