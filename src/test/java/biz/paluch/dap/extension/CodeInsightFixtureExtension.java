
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Function;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * JUnit Jupiter extension that creates, injects, and tears down a
 * {@link CodeInsightTestFixture} for each test method.
 *
 * <p>Supported injection targets:
 * <ul>
 * <li>non-static, non-final fields of type {@link CodeInsightTestFixture}</li>
 * <li>non-static, non-final fields of type {@link IdeaProjectTestFixture}</li>
 * <li>test method or lifecycle method parameters of those same types</li>
 * </ul>
 * 
 * @author Mark Paluch
 */
class CodeInsightFixtureExtension
		implements TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback, ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(CodeInsightFixtureExtension.class);

	private static final String STORE_KEY = "codeInsightFixtureResource";

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
		for (Field field : findInjectableFields(testInstance.getClass())) {
			validateField(field);
		}
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		FixtureResource resource = getOrCreateResource(context);
		injectFields(context.getRequiredTestInstance(), resource);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {

		Exception failure = null;

		try {
			FixtureResource resource = removeResource(context);
			if (resource != null) {
				resource.close();
			}
		} catch (RuntimeException ex) {
			failure = ex;
		}

		try {
			context.getTestInstance().ifPresent(this::clearFieldsQuietly);
		} catch (Exception ex) {
			if (failure == null) {
				failure = ex;
			} else {
				failure.addSuppressed(ex);
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		if (parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
			return false;
		}
		return isSupportedType(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
		Class<?> parameterType = parameterContext.getParameter().getType();
		try {
			FixtureResource resource = getOrCreateResource(context);
			return resolveValue(parameterType, resource);
		} catch (Exception ex) {
			throw new ParameterResolutionException(
					"Failed to resolve parameter of type " + parameterType.getName(), ex);
		}
	}

	private FixtureResource getOrCreateResource(ExtensionContext context) {
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		return store.computeIfAbsent(STORE_KEY, c -> createResource(context),
				FixtureResource.class);
	}

	private FixtureResource removeResource(ExtensionContext context) {
		return context.getStore(NAMESPACE).remove(STORE_KEY, FixtureResource.class);
	}

	private FixtureResource createResource(ExtensionContext context) {

		CodeInsightFixture configuration = findConfiguration(context);

		String fixtureName = configuration.fixtureName().isBlank()
				? context.getRequiredTestClass().getSimpleName() + "#" + context.getRequiredTestMethod().getName()
				: configuration.fixtureName();

		TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory()
				.createLightFixtureBuilder(fixtureName);

		IdeaProjectTestFixture ideaProjectFixture = builder.getFixture();
		CodeInsightTestFixture codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory()
				.createCodeInsightFixture(ideaProjectFixture);

		try {
			codeInsightFixture.setUp();
			return new FixtureResource(ideaProjectFixture, codeInsightFixture);
		} catch (Exception ex) {
			try {
				codeInsightFixture.tearDown();
			} catch (Exception tearDownEx) {
				ex.addSuppressed(tearDownEx);
			}
			throw new UndeclaredThrowableException(ex);
		}
	}

	private void injectFields(Object testInstance, FixtureResource resource) {
		setFields(testInstance, field -> resolveValue(field.getType(), resource));
	}

	private void clearFieldsQuietly(Object testInstance) {
		setFields(testInstance, field -> null);
	}

	private void setFields(Object testInstance, Function<Field, Object> valueProvider) {


		for (Field field : findInjectableFields(testInstance.getClass())) {
			ReflectionUtils.makeAccessible(field);
			try {
				field.set(testInstance, valueProvider.apply(field));
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private Object resolveValue(Class<?> requestedType, FixtureResource resource) {
		if (requestedType.isInstance(resource.codeInsightFixture)) {
			return resource.codeInsightFixture;
		}
		if (requestedType.isInstance(resource.ideaProjectFixture)) {
			return resource.ideaProjectFixture;
		}
		throw new ExtensionConfigurationException(
				"Unsupported injection target type: " + requestedType.getName());
	}

	private CodeInsightFixture findConfiguration(ExtensionContext context) {
		CodeInsightFixture annotation = context.getRequiredTestClass().getAnnotation(CodeInsightFixture.class);
		if (annotation == null) {
			throw new ExtensionConfigurationException(
					"@" + CodeInsightFixture.class.getSimpleName() + " must be present on the test class");
		}
		return annotation;
	}

	private static boolean isSupportedType(Class<?> type) {
		return CodeInsightTestFixture.class.isAssignableFrom(type)
				|| IdeaProjectTestFixture.class.isAssignableFrom(type);
	}

	private static List<Field> findInjectableFields(Class<?> testClass) {
		List<Field> fields = new ArrayList<>();
		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!field.isSynthetic() && isSupportedType(field.getType())) {
					fields.add(field);
				}
			}
			current = current.getSuperclass();
		}
		return fields;
	}

	private static void validateField(Field field) {
		int modifiers = field.getModifiers();
		if (Modifier.isStatic(modifiers)) {
			throw new ExtensionConfigurationException(
					"Fixture field must not be static: " + field);
		}
		if (Modifier.isFinal(modifiers)) {
			throw new ExtensionConfigurationException(
					"Fixture field must not be final: " + field);
		}
	}

	private record FixtureResource(IdeaProjectTestFixture ideaProjectFixture,
			CodeInsightTestFixture codeInsightFixture) {

		private FixtureResource(
				IdeaProjectTestFixture ideaProjectFixture,
				CodeInsightTestFixture codeInsightFixture) {

			this.ideaProjectFixture = Objects.requireNonNull(ideaProjectFixture, "ideaProjectFixture must not be null");
			this.codeInsightFixture = Objects.requireNonNull(codeInsightFixture, "codeInsightFixture must not be null");
		}

		private void close() throws Exception {
			codeInsightFixture.tearDown();
		}

	}

}
