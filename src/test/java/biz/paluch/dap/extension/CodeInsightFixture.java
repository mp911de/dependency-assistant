/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Composed annotation for IntelliJ Code Insight tests running on JUnit 5.
 *
 * <p>Includes:
 * <ul>
 * <li>IntelliJ test application bootstrap</li>
 * <li>EDT execution with write intent</li>
 * <li>Automatic {@code CodeInsightTestFixture} lifecycle management</li>
 * </ul>
 *
 * @author Mark Paluch
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@TestApplication
@RunInEdt(writeIntent = true)
@ExtendWith(CodeInsightFixtureExtension.class)
public @interface CodeInsightFixture {

	/**
	 * Optional fixture name. If left blank, the extension will derive one from the
	 * test class and method.
	 */
	String fixtureName() default "";

}
