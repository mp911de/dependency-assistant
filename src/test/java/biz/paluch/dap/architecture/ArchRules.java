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

import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Factory for reusable ArchUnit rules.
 *
 * @author Mark Paluch
 */
class ArchRules {

	private static final String ROOT = "biz.paluch.dap";

	private ArchRules() {
	}

	/**
	 * Create a predicate that matches classes that reside in the given package or
	 * any of the given allowed packages.
	 */
	static DescribedPredicate<JavaClass> residesInAnyPackage(String packageUnderTest, String... allowedPackages) {

		List<String> packages = Stream.concat(Stream.of(packageUnderTest), Stream.of(allowedPackages))
				.map(ArchRules::expandPackage)
				.toList();

		return JavaClass.Predicates.resideOutsideOfPackage(ROOT + "..")
				.or(JavaClass.Predicates.resideInAnyPackage(packages.toArray(String[]::new)));
	}

	static String expandPackage(String pkg) {
		if (pkg.startsWith(ROOT)) {
			return pkg;
		}
		return ROOT + "." + pkg;
	}

}
