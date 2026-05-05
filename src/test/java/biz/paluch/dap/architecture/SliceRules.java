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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
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
		return new ClassesAndClosedHierarchies();
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

	/**
	 * Slice assignment that maps each class to its top-level enclosing type and
	 * additionally collapses same-package supertype/subtype chains into a single
	 * slice. The slice identifier is the lexicographically lowest type in the
	 * closed hierarchy, which keeps visitor- and template-style designs (a base
	 * type and its same-package implementations referencing each other) from being
	 * reported as cycles.
	 */
	static class ClassesAndClosedHierarchies implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			JavaClass topLevel = javaClass;

			while (topLevel.getEnclosingClass().isPresent()) {
				topLevel = topLevel.getEnclosingClass().get();
			}

			return SliceIdentifier.of(findTypeFamilyAnchor(topLevel));
		}

		@Override
		public String getDescription() {
			return "Class";
		}

		private static String findTypeFamilyAnchor(JavaClass topLevel) {
			String packageName = topLevel.getPackageName();
			Set<String> visited = new TreeSet<>();
			return findTypeFamilyAnchor(topLevel, packageName, visited).getName();
		}

		private static JavaClass findTypeFamilyAnchor(JavaClass type, String packageName, Set<String> visited) {

			if (!visited.add(type.getName())) {
				return type;
			}

			List<JavaClass> localParents = new ArrayList<>();

			type.getRawSuperclass()
					.filter(candidate -> isSamePackageType(candidate, packageName))
					.ifPresent(localParents::add);

			for (JavaClass interfaceType : type.getRawInterfaces()) {
				if (isSamePackageType(interfaceType, packageName)) {
					localParents.add(interfaceType);
				}
			}

			if (localParents.isEmpty()) {
				return type;
			}

			localParents.sort(Comparator.comparing(JavaClass::getName));

			JavaClass anchor = type;

			for (JavaClass candidate : localParents) {
				JavaClass resolved = findTypeFamilyAnchor(candidate, packageName, visited);

				if (anchor == type || resolved.getName().compareTo(anchor.getName()) < 0) {
					anchor = resolved;
				}
			}

			return anchor;
		}

		private static boolean isSamePackageType(JavaClass type, String packageName) {
			return !type.isPrimitive() && !type.isArray() && !type.getModifiers().contains(JavaModifier.PRIVATE)
					&& packageName.equals(type.getPackageName());
		}

	}

}
