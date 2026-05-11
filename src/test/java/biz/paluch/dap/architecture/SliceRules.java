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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

/**
 * Factory for reusable {@link SliceAssignment} strategies that complement
 * ArchUnit's built-in package-pattern slicing.
 * <p>This class is a stateless factory and is not meant to be instantiated.
 *
 * @author Mark Paluch
 */
class SliceRules {

	private SliceRules() {
	}

	/**
	 * Slice assignment that maps each class to its declared package. Every package
	 * and sub-package becomes its own slice, so cycles between packages at any
	 * nesting level are detected.
	 *
	 * @return a {@link SliceAssignment} keyed by the fully qualified package name.
	 */
	static SliceAssignment allPackages() {
		return new AllPackages();
	}

	/**
	 * Slice assignment that maps each class to its top-level enclosing type and
	 * additionally collapses same-package supertype/subtype chains into a single
	 * slice. The slice identifier is the lexicographically lowest type in the
	 * closed hierarchy, which keeps visitor- and template-style designs (a base
	 * type and its same-package implementations referencing each other) from being
	 * reported as cycles.
	 *
	 * @return a {@link SliceAssignment} keyed by the type-family anchor.
	 */
	static SliceAssignment classesAndClosedHierarchies() {
		return new Classes();
	}


	/**
	 * Slice assignment that maps each class to its declared package. Every package
	 * and sub-package becomes its own slice, so cycles between packages at any
	 * nesting level are detected.
	 */
	private static class AllPackages implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			return SliceIdentifier.of(javaClass.getPackageName());
		}

		@Override
		public String getDescription() {
			return "Package";
		}

	}

	static class Classes implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			return SliceIdentifier.of(javaClass.getName());
		}

		@Override
		public String getDescription() {
			return "Class";
		}

	}

}
