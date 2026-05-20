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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
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
 * JUnit Jupiter extension that creates one light IntelliJ project per test
 * class and injects the corresponding {@link Project}.
 *
 * @author Mark Paluch
 */
class ProjectExtension implements TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback, AfterAllCallback,
		ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(ProjectExtension.class);

	private static final String STORE_KEY = "projectResource";

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) {

		if (!isActive(context)) {
			return;
		}

		for (Field field : findInjectableFields(testInstance.getClass())) {
			validateField(field);
		}
	}

	@Override
	public void beforeEach(ExtensionContext context) {

		if (!isActive(context)) {
			return;
		}

		ProjectResource resource = getOrCreateResource(context);
		injectFields(context.getRequiredTestInstance(), resource);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {

		if (!isActive(context)) {
			return;
		}

		context.getTestInstance().ifPresent(this::clearFieldsQuietly);
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {

		if (!isActive(context)) {
			return;
		}

		ProjectResource resource = removeResource(context);
		if (resource != null) {
			resource.close();
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		if (!isActive(extensionContext) || parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
			return false;
		}

		return isSupportedType(parameterContext.getParameter().getType());
	}

	@Override
	public @Nullable Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		if (!isActive(context)) {
			return null;
		}

		Class<?> parameterType = parameterContext.getParameter().getType();
		try {
			return resolveValue(parameterType, getOrCreateResource(context));
		} catch (Exception ex) {
			throw new ParameterResolutionException(
					"Failed to resolve parameter of type " + parameterType.getName(), ex);
		}
	}

	private ProjectResource getOrCreateResource(ExtensionContext context) {
		return getClassContext(context).getStore(NAMESPACE).computeIfAbsent(STORE_KEY,
				key -> createResource(context), ProjectResource.class);
	}

	static Project getProject(ExtensionContext context) {
		ProjectResource resource = getClassContext(context).getStore(NAMESPACE).get(STORE_KEY, ProjectResource.class);
		if (resource == null) {
			throw new ExtensionConfigurationException(
					"No Project available in current extension context. Ensure @IdeaProjectTests is present and "
							+ ProjectExtension.class.getSimpleName() + " has run beforeEach");
		}
		return resource.project();
	}

	private @Nullable ProjectResource removeResource(ExtensionContext context) {
		return getClassContext(context).getStore(NAMESPACE).remove(STORE_KEY, ProjectResource.class);
	}

	private static ExtensionContext getClassContext(ExtensionContext context) {

		ExtensionContext current = context;
		while (current.getTestMethod().isPresent() && current.getParent().isPresent()) {
			current = current.getParent().get();
		}
		return current;
	}

	private static ProjectResource createResource(ExtensionContext context) {

		IdeaProjectTests configuration = getRequiredConfiguration(context);
		String fixtureName = configuration.fixtureName().isBlank()
				? context.getRequiredTestClass().getSimpleName()
				: configuration.fixtureName();

		IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
		TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createLightFixtureBuilder(fixtureName);
		IdeaProjectTestFixture projectFixture = builder.getFixture();

		try {
			EdtTestUtil.runInEdtAndWait(projectFixture::setUp);
			return new ProjectResource(projectFixture);
		} catch (Exception ex) {
			try {
				EdtTestUtil.runInEdtAndWait(projectFixture::tearDown);
			} catch (Exception tearDownEx) {
				ex.addSuppressed(tearDownEx);
			}
			throw new ExtensionConfigurationException("Failed to create light project fixture", ex);
		}
	}

	private void injectFields(Object testInstance, ProjectResource resource) {
		setFields(testInstance, field -> resolveValue(field.getType(), resource));
	}

	private void clearFieldsQuietly(Object testInstance) {
		setFields(testInstance, field -> null);
	}

	private void setFields(Object testInstance, Function<Field, @Nullable Object> valueProvider) {

		for (Field field : findInjectableFields(testInstance.getClass())) {
			ReflectionUtils.makeAccessible(field);
			try {
				field.set(testInstance, valueProvider.apply(field));
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private Object resolveValue(Class<?> requestedType, ProjectResource resource) {
		if (requestedType.isInstance(resource.project())) {
			return resource.project();
		}
		throw new ExtensionConfigurationException(
				"Unsupported injection target type: " + requestedType.getName());
	}

	static boolean isActive(ExtensionContext context) {
		return context.getTestClass().isPresent()
				&& context.getRequiredTestClass().isAnnotationPresent(IdeaProjectTests.class);
	}

	private static IdeaProjectTests getRequiredConfiguration(ExtensionContext context) {

		IdeaProjectTests annotation = context.getRequiredTestClass().getAnnotation(IdeaProjectTests.class);
		if (annotation == null) {
			throw new ExtensionConfigurationException(
					"@" + IdeaProjectTests.class.getSimpleName() + " must be present on the test class");
		}
		return annotation;
	}

	private static boolean isSupportedType(Class<?> type) {
		return Project.class.isAssignableFrom(type);
	}

	private static List<Field> findInjectableFields(Class<?> testClass) {
		List<Field> fields = new ArrayList<>();
		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!field.isSynthetic()
						&& isSupportedType(field.getType()) && field.isAnnotationPresent(TestFixture.class)) {
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

	private record ProjectResource(IdeaProjectTestFixture projectFixture) {

		private Project project() {
			return projectFixture.getProject();
		}

		private void close() throws Exception {
			EdtTestUtil.runInEdtAndWait(projectFixture::tearDown);
		}

	}

}
