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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Resolves {@link StringTest} values for {@link String} test method parameters.
 *
 * @author Mark Paluch
 */
class StringTestExtension implements ParameterResolver {

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		if (parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
			return false;
		}
		return parameterContext.getParameter().getType() == String.class
				&& findStringTest(extensionContext).isPresent();
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		return findStringTest(extensionContext)
				.map(StringTest::value)
				.orElseThrow(() -> new ParameterResolutionException(
						"No @StringTest annotation found for " + parameterContext.getDeclaringExecutable()));
	}

	private static Optional<StringTest> findStringTest(ExtensionContext context) {
		return context.getTestMethod().flatMap(StringTestExtension::findStringTest);
	}

	private static Optional<StringTest> findStringTest(Method method) {
		return AnnotationSupport.findAnnotation(method, StringTest.class);
	}

}
