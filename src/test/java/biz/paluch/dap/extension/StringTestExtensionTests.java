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

package biz.paluch.dap.extension;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StringTestExtension}.
 *
 * @author Mark Paluch
 */
class StringTestExtensionTests {

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void injectsStringLiteral(String value) {

		assertThat(value)
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@StringTest("""
			first line
			second line
			""")
	void injectsTextBlock(String value) {

		assertThat(value).isEqualTo("""
				first line
				second line
				""");
	}

}
