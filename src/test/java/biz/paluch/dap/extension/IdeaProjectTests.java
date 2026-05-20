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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Composed annotation for IntelliJ project-level tests based on JUnit Jupiter.
 * <p>Applying this annotation to a test class creates a single light IntelliJ
 * project shared by all test methods in the class and tears it down after the
 * last method has run. The active {@link com.intellij.openapi.project.Project}
 * is available through {@link TestFixture}-annotated fields and through method
 * parameters of type {@code Project}. Test methods execute on the Event
 * Dispatch Thread with a write-intent lock so that PSI and VFS operations are
 * permitted without additional wrapping.
 * <p>Use this annotation when a test only needs a
 * {@link com.intellij.openapi.project.Project} and project files (for example,
 * declarative {@link ProjectFile} setup). Use {@link CodeInsightFixtureTests}
 * instead when the test needs an editor, caret-driven actions, completion, or
 * other Code Insight fixture features.
 *
 * @author Mark Paluch
 * @see ProjectExtension
 * @see ProjectFileExtension
 * @see TestFixture
 * @see ProjectFile
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@RunInEdt(writeIntent = true)
@ExtendWith({ProjectExtension.class, ProjectFileExtension.class})
public @interface IdeaProjectTests {

	/**
	 * Fixture name passed to the light project builder; useful for disambiguating
	 * fixtures in logs and test reports.
	 * <p>Defaults to the test class's {@code Class.getSimpleName()} when empty.
	 *
	 * @return the light project fixture name, or an empty string to derive it from
	 * the test class name.
	 */
	String fixtureName() default "";

}
