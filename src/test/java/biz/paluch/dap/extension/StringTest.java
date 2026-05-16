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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Composed JUnit Jupiter test annotation that injects a string fixture into
 * {@link String} parameters of the annotated test method.
 *
 * <p>Use this annotation when a test has one primary string input and keeping
 * the input next to the test declaration makes the method body easier to read.
 * The annotation is itself meta-annotated with {@link Test}, so methods have
 * the same test semantics as ordinary Jupiter tests, including parameter
 * resolution by registered {@link ParameterResolver ParameterResolvers}.
 *
 * <p>The configured value is passed through unchanged. Java text blocks can be
 * used for multi-line fixtures; their value is the string produced by the Java
 * compiler.
 *
 * @author Mark Paluch
 * @see Test
 * @see ParameterResolver
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Test
@ExtendWith(StringTestExtension.class)
public @interface StringTest {

	/**
	 * String fixture to inject into supported test method parameters.
	 *
	 * @return the string fixture to inject.
	 */
	String value();

}
