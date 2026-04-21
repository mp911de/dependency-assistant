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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures the editor with a named file via
 * {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#configureByText}.
 * <p>Only one {@code @EditorFile} declaration per test method is supported. The
 * resulting {@link com.intellij.psi.PsiFile} is registered under its name and
 * participates in name-based {@link ProjectFile} parameter lookup.
 *
 * @author Mark Paluch
 * @see ProjectFile
 * @see ProjectFileExtension
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EditorFile {

	/**
	 * The file name passed to
	 * {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#configureByText}.
	 *
	 * @return the editor file name; must not be {@literal null} or empty.
	 */
	String name();

	/**
	 * The file content.
	 *
	 * @return the editor file content; defaults to an empty file.
	 */
	String content() default "";

}
