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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import org.springframework.util.Assert;

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
	 * Slice assignment that maps each class to its fully qualified class name,
	 * unless or closed hierarchy declaration claims the class first. Matching
	 * hierarchy members share the root type's slice identifier.
	 *
	 * <p>Usage examples:
	 *
	 * <pre class="code">
	 * // Plain class slicing: each class is a slice.
	 * SliceRules.classes()
	 *
	 * // Explicitly treat DeclarationSource and its subtypes as one slice.
	 * SliceRules.classes(builder -> {
	 *     builder.withClosedHierarchy(DeclarationSource.class);
	 * });
	 * </pre>
	 *
	 * @param builderConsumer consumer to configure the {@link AssignmentBuilder}.
	 * @return a {@link SliceAssignment} keyed by class or explicit family.
	 */
	static SliceAssignment classes(Consumer<AssignmentBuilder> builderConsumer) {

		List<ClosedHierarchy> closedHierarchies = new ArrayList<>();
		List<StrictClosedHierarchy> strictClosedHierarchies = new ArrayList<>();

		AssignmentBuilder builder = new AssignmentBuilder() {

			@Override
			public AssignmentBuilder with(ClosedHierarchy hierarchy) {

				closedHierarchies.add(hierarchy);
				return this;
			}

			@Override
			public AssignmentBuilder with(StrictClosedHierarchy hierarchy) {

				strictClosedHierarchies.add(hierarchy);
				return this;
			}

		};

		builderConsumer.accept(builder);
		return new ClassesWithClosedHierarchies(closedHierarchies, strictClosedHierarchies);
	}

	/**
	 * Builder for {@link #classes(Consumer) class slice assignment} configuration.
	 */
	public interface AssignmentBuilder {

		/**
		 * Configure the assignment to treat the given root type as a closed
		 * @param fqcn fully qualified class name of the root type.
		 * @return {@code this} builder.
		 */
		default AssignmentBuilder withClosedHierarchy(String fqcn) {
			return with(new ClosedHierarchy(fqcn));
		}

		/**
		 * Configure the assignment to treat the given root type as a closed
		 * @param rootType the root type to use.
		 * @return {@code this} builder.
		 */
		default AssignmentBuilder withClosedHierarchy(Class<?> rootType) {
			return with(ClosedHierarchy.from(rootType));
		}

		/**
		 * Configure the assignment to treat the given root type as a closed
		 * @param fqcn fully qualified class name of the root type.
		 * @return {@code this} builder.
		 */
		default AssignmentBuilder withStrictClosedHierarchy(String fqcn) {
			return with(new StrictClosedHierarchy(fqcn));
		}

		/**
		 * Configure the assignment to treat the given root type as a closed
		 * @param rootType the root type to use.
		 * @return {@code this} builder.
		 */
		default AssignmentBuilder withStrictClosedHierarchy(Class<?> rootType) {
			return with(StrictClosedHierarchy.from(rootType));
		}

		/**
		 * Configure the closed hierarchy as an individual slice.
		 * @param hierarchy the closed hierarchy to consider.
		 * @return {@code this} builder.
		 */
		AssignmentBuilder with(ClosedHierarchy hierarchy);

		/**
		 * Configure the closed hierarchy as an individual slice.
		 * @param hierarchy the closed hierarchy to consider.
		 * @return {@code this} builder.
		 */
		AssignmentBuilder with(StrictClosedHierarchy hierarchy);

	}

	private static JavaClass findTopLevelType(JavaClass javaClass) {
		JavaClass topLevel = javaClass;

		while (topLevel.getEnclosingClass().isPresent()) {
			topLevel = topLevel.getEnclosingClass().get();
		}

		return topLevel;
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

	public record ClosedHierarchy(String rootTypeName) {

		public ClosedHierarchy {
			Assert.hasText(rootTypeName, "Root type name must not be blank");
		}

		public static ClosedHierarchy from(Class<?> rootType) {
			return new ClosedHierarchy(rootType.getName());
		}

		SliceIdentifier identifier() {
			return SliceIdentifier.of(this.rootTypeName);
		}

		boolean matches(JavaClass javaClass, JavaClass topLevel) {
			return belongsToRoot(javaClass) || isAssignableToRoot(javaClass) || isAssignableToRoot(topLevel);
		}

		private boolean belongsToRoot(JavaClass javaClass) {
			JavaClass current = javaClass;

			while (true) {
				if (current.getName().equals(this.rootTypeName)) {
					return true;
				}

				if (current.getEnclosingClass().isEmpty()) {
					return false;
				}

				current = current.getEnclosingClass().get();
			}
		}

		private boolean isAssignableToRoot(JavaClass javaClass) {
			return !javaClass.isPrimitive() && !javaClass.isArray() && javaClass.isAssignableTo(this.rootTypeName);
		}

	}

	/**
	 * Same-package and package-protected.
	 */
	public record StrictClosedHierarchy(String rootTypeName) {

		public StrictClosedHierarchy {
			Assert.hasText(rootTypeName, "Root type name must not be blank");
		}

		public static StrictClosedHierarchy from(Class<?> rootType) {
			return new StrictClosedHierarchy(rootType.getName());
		}

		SliceIdentifier identifier() {
			return SliceIdentifier.of(this.rootTypeName);
		}

		boolean matches(JavaClass javaClass, JavaClass topLevel) {
			boolean b = belongsToRoot(javaClass) ||
					(javaClass.getPackageName().equals(topLevel.getPackageName())
							&& rootTypeName.startsWith(javaClass.getPackageName())
							&& isPackagePrivate(javaClass.getModifiers()) && (isAssignableToRoot(javaClass)
									|| isAssignableToRoot(topLevel)));

			return b;
		}

		private boolean isPackagePrivate(Set<JavaModifier> modifiers) {
			return !modifiers.contains(JavaModifier.PROTECTED) &&
					!modifiers.contains(JavaModifier.PRIVATE) &&
					!modifiers.contains(JavaModifier.PUBLIC);
		}

		private boolean belongsToRoot(JavaClass javaClass) {
			JavaClass current = javaClass;

			while (true) {
				if (current.getName().equals(this.rootTypeName)) {
					return true;
				}

				if (current.getEnclosingClass().isEmpty()) {
					return false;
				}

				current = current.getEnclosingClass().get();
			}
		}

		private boolean isAssignableToRoot(JavaClass javaClass) {
			return !javaClass.isPrimitive() && !javaClass.isArray() && javaClass.isAssignableTo(this.rootTypeName);
		}

	}

	/**
	 * Slice assignment that maps each class to its fully qualified class name.
	 */
	private static class Classes implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			return SliceIdentifier.of(javaClass.getName());
		}

		@Override
		public String getDescription() {
			return "Class";
		}

	}

	/**
	 * Slice assignment that applies explicitly declared closed hierarchies before
	 * falling back to plain class slices.
	 */
	private static class ClassesWithClosedHierarchies implements SliceAssignment {

		private final List<ClosedHierarchy> closedHierarchies;

		private final List<StrictClosedHierarchy> strictClosedHierarchies;

		private ClassesWithClosedHierarchies(List<ClosedHierarchy> closedHierarchies,
				List<StrictClosedHierarchy> strictClosedHierarchies) {
			this.closedHierarchies = closedHierarchies;
			this.strictClosedHierarchies = strictClosedHierarchies;
		}

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {

			JavaClass topLevel = findTopLevelType(javaClass);

			for (ClosedHierarchy hierarchy : this.closedHierarchies) {
				if (hierarchy.matches(javaClass, topLevel)) {
					return hierarchy.identifier();
				}
			}

			for (StrictClosedHierarchy hierarchy : this.strictClosedHierarchies) {
				if (hierarchy.matches(javaClass, topLevel)) {
					return hierarchy.identifier();
				}
			}

			return SliceIdentifier.of(topLevel.getName());
		}

		@Override
		public String getDescription() {
			return "Class with declared closed hierarchies";
		}

	}

}
