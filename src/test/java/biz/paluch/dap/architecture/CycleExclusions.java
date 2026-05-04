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

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Value object describing exclusions for dependency cycle checks.
 * <p>Exclusions require a non-blank reason in order to capture intentional
 * design decisions, even if the reason is not forwarded to ArchUnit itself.
 *
 * @author Mark Paluch
 */
class CycleExclusions {

	private final List<PackageExclusion> packageExclusions;

	private final List<ClassExclusion> classExclusions;

	private CycleExclusions(List<PackageExclusion> packageExclusions, List<ClassExclusion> classExclusions) {
		this.packageExclusions = List.copyOf(packageExclusions);
		this.classExclusions = List.copyOf(classExclusions);
	}

	static CycleExclusions none() {
		return new CycleExclusions(List.of(), List.of());
	}

	CycleExclusions excludingPackage(String packageName, String reason) {
		List<PackageExclusion> packageExclusions = new ArrayList<>(this.packageExclusions);
		packageExclusions.add(new PackageExclusion(packageName, reason));

		return new CycleExclusions(packageExclusions, this.classExclusions);
	}

	CycleExclusions excludingClass(Class<?> type, String reason) {
		return excludingClass(type.getName(), reason);
	}

	CycleExclusions excludingClass(String className, String reason) {
		List<ClassExclusion> classExclusions = new ArrayList<>(this.classExclusions);
		classExclusions.add(new ClassExclusion(className, reason));

		return new CycleExclusions(this.packageExclusions, classExclusions);
	}

	boolean isExcluded(JavaClass javaClass) {

		if (javaClass.getName().endsWith(".package-info")) {
			return true;
		}

		return isExcludedByPackage(javaClass) || isExcludedByClass(javaClass);
	}

	private boolean isExcludedByPackage(JavaClass javaClass) {
		for (PackageExclusion exclusion : this.packageExclusions) {
			if (exclusion.matches(javaClass)) {
				return true;
			}
		}

		return false;
	}

	private boolean isExcludedByClass(JavaClass javaClass) {
		for (ClassExclusion exclusion : this.classExclusions) {
			if (exclusion.matches(javaClass)) {
				return true;
			}
		}

		return false;
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}

		return value;
	}

	private record PackageExclusion(String packageName, String reason) {

		PackageExclusion {
			requireText(packageName, "Package name must not be blank");
			requireText(reason, "Exclusion reason must not be blank");
		}

		boolean matches(JavaClass javaClass) {
			String candidate = javaClass.getPackageName();
			return candidate.equals(this.packageName) || candidate.startsWith(this.packageName + ".");
		}

	}

	private record ClassExclusion(String className, String reason) {

		ClassExclusion {
			requireText(className, "Class name must not be blank");
			requireText(reason, "Exclusion reason must not be blank");
		}

		boolean matches(JavaClass javaClass) {
			return javaClass.getName().equals(this.className);
		}

	}

	@Override
	public String toString() {
		return "CycleExclusions[packages=%s, classes=%s]".formatted(this.packageExclusions, this.classExclusions);
	}

}
