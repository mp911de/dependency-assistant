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

package biz.paluch.dap.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * {@link ImportOption} that excludes test output locations for ArchUnit
 * imports.
 * <p>In addition to ArchUnit's built-in test output detection, this variant
 * also excludes IntelliJ Platform Gradle Plugin instrumented test classes.
 *
 * @author Mark Paluch
 */
class ExcludeInstrumentedTestCode implements ImportOption {

	private String INSTRUMENTED_TEST_CODE = "/build/instrumented/instrumentTestCode/";

	@Override
	public boolean includes(Location location) {

		return ImportOption.Predefined.DO_NOT_INCLUDE_TESTS.includes(location)
				&& !location.contains(INSTRUMENTED_TEST_CODE);
	}

}

