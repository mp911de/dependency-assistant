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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a file with the test project via
 * {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#addFileToProject}.
 *
 * <p>When used as a <em>method-level</em> annotation, both a name and content
 * must be provided. When used as a <em>parameter-level</em> annotation, only
 * the name is required and acts as a lookup key for an already-registered file.
 *
 * <p>{@link #value} and {@link #name} are aliases of each other; exactly one
 * must be set to a non-empty value.
 *
 * @author Mark Paluch
 * @see EditorFile
 * @see ProjectFileExtension
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ProjectFiles.class)
@Documented
public @interface ProjectFile {

	/**
	 * Alias for {@link #name}.
	 */
	String value() default "";

	/**
	 * The file name or path relative to the project root. Alias for {@link #value}.
	 */
	String name() default "";

	/**
	 * The file content. Relevant only for method-level use; ignored on parameters.
	 */
	String content() default "";

}
