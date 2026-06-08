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
	 * unless a closed hierarchy declaration supports the class first. Matching
	 * hierarchy members share the root type's slice identifier.
	 *
	 * <p>Usage examples:
	 *
	 * <pre class="code">
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

		List<Hierarchy> hierarchies = new ArrayList<>();

		AssignmentBuilder builder = new AssignmentBuilder() {

			@Override
			public AssignmentBuilder with(ClosedHierarchy hierarchy) {

				hierarchies.add(hierarchy);
				return this;
			}

			@Override
			public AssignmentBuilder with(StrictClosedHierarchy hierarchy) {

				hierarchies.add(hierarchy);
				return this;
			}

		};

		builderConsumer.accept(builder);
		return new ClassesWithClosedHierarchies(hierarchies);
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

	private static boolean belongsToRoot(JavaClass javaClass, String rootTypeName) {
		return isNestedInRoot(javaClass, rootTypeName) || isSealedMemberOfRoot(javaClass, rootTypeName);
	}

	private static boolean isNestedInRoot(JavaClass javaClass, String rootTypeName) {
		JavaClass current = javaClass;

		while (true) {
			if (current.getName().equals(rootTypeName)) {
				return true;
			}

			if (current.getEnclosingClass().isEmpty()) {
				return false;
			}

			current = current.getEnclosingClass().get();
		}
	}

	private static boolean isSealedMemberOfRoot(JavaClass javaClass, String rootTypeName) {

		if (javaClass.isPrimitive() || javaClass.isArray()) {
			return false;
		}

		try {
			Class<?> candidate = javaClass.reflect();
			Class<?> rootType = Class.forName(rootTypeName, false, candidate.getClassLoader());
			return isPermittedBy(rootType, candidate);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			return false;
		}
	}

	private static boolean isPermittedBy(Class<?> rootType, Class<?> candidate) {

		Class<?>[] permittedSubclasses = rootType.getPermittedSubclasses();
		if (permittedSubclasses == null) {
			return false;
		}

		for (Class<?> permittedSubclass : permittedSubclasses) {
			if (permittedSubclass.equals(candidate) || isPermittedBy(permittedSubclass, candidate)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isAssignableToRoot(JavaClass javaClass, String rootTypeName) {
		return !javaClass.isPrimitive() && !javaClass.isArray() && javaClass.isAssignableTo(rootTypeName);
	}

	private static boolean isInPackage(String typeName, String packageName) {
		return typeName.startsWith(packageName + ".");
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

	private interface Hierarchy {

		SliceIdentifier identifier();

		boolean matchesByMembership(JavaClass javaClass);

		boolean matchesByAssignability(JavaClass javaClass, JavaClass topLevel);

	}

	public record ClosedHierarchy(String rootTypeName) implements Hierarchy {

		public ClosedHierarchy {
			Assert.hasText(rootTypeName, "Root type name must not be blank");
		}

		public static ClosedHierarchy from(Class<?> rootType) {
			return new ClosedHierarchy(rootType.getName());
		}

		@Override
		public SliceIdentifier identifier() {
			return SliceIdentifier.of(this.rootTypeName);
		}

		@Override
		public boolean matchesByMembership(JavaClass javaClass) {
			return belongsToRoot(javaClass, this.rootTypeName);
		}

		@Override
		public boolean matchesByAssignability(JavaClass javaClass, JavaClass topLevel) {
			return isAssignableToRoot(javaClass, this.rootTypeName)
			       || isAssignableToRoot(topLevel, this.rootTypeName);
		}

	}

	/**
	 * Same-package and package-protected.
	 */
	public record StrictClosedHierarchy(String rootTypeName) implements Hierarchy {

		public StrictClosedHierarchy {
			Assert.hasText(rootTypeName, "Root type name must not be blank");
		}

		public static StrictClosedHierarchy from(Class<?> rootType) {
			return new StrictClosedHierarchy(rootType.getName());
		}

		@Override
		public SliceIdentifier identifier() {
			return SliceIdentifier.of(this.rootTypeName);
		}

		@Override
		public boolean matchesByMembership(JavaClass javaClass) {
			return belongsToRoot(javaClass, this.rootTypeName);
		}

		@Override
		public boolean matchesByAssignability(JavaClass javaClass, JavaClass topLevel) {
			return javaClass.getPackageName().equals(topLevel.getPackageName())
			       && isInPackage(this.rootTypeName, javaClass.getPackageName())
			       && isPackagePrivate(javaClass.getModifiers()) && (isAssignableToRoot(javaClass, this.rootTypeName)
			                                                         || isAssignableToRoot(topLevel, this.rootTypeName));
		}

		private boolean isPackagePrivate(Set<JavaModifier> modifiers) {
			return !modifiers.contains(JavaModifier.PROTECTED) &&
					!modifiers.contains(JavaModifier.PRIVATE) &&
					!modifiers.contains(JavaModifier.PUBLIC);
		}

	}

	/**
	 * Slice assignment that applies explicitly declared closed hierarchies before
	 * falling back to plain class slices.
	 */
	private static class ClassesWithClosedHierarchies implements SliceAssignment {

		private final List<Hierarchy> hierarchies;

		private ClassesWithClosedHierarchies(List<Hierarchy> hierarchies) {
			this.hierarchies = List.copyOf(hierarchies);
		}

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {

			JavaClass topLevel = findTopLevelType(javaClass);

			for (Hierarchy hierarchy : this.hierarchies) {
				if (hierarchy.matchesByMembership(javaClass)) {
					return hierarchy.identifier();
				}
			}

			for (Hierarchy hierarchy : this.hierarchies) {
				if (hierarchy.matchesByAssignability(javaClass, topLevel)) {
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
