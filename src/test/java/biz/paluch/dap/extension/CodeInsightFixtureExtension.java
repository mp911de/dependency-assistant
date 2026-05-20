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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.*;
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
		implements TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback, AfterAllCallback,
		ParameterResolver,
		InvocationInterceptor {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(CodeInsightFixtureExtension.class);

	private static final String STORE_KEY = "codeInsightFixtureContext";

	/**
	 * Package-private accessor for use by collaborating extensions that require the
	 * fixture after it has been set up by this extension's {@code beforeEach}.
	 */
	static CodeInsightTestFixture getFixture(ExtensionContext context) {
		FixtureResource resource = getClassFixtureContext(context).getResource(context);
		if (resource == null) {
			throw new ExtensionConfigurationException(
					"No CodeInsightTestFixture available in current extension context. "
							+ "Ensure @CodeInsightFixtureTests is present and "
							+ CodeInsightFixtureExtension.class.getSimpleName() + " has run beforeEach");
		}
		return resource.codeInsightFixture();
	}

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) {

		if (!hasConfiguration(context)) {
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

		FixtureResource resource = getOrCreateResource(context);
		injectFields(context.getRequiredTestInstance(), resource);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {

		if (!isActive(context)) {
			return;
		}

		Exception failure = null;

		try {
			getClassFixtureContext(context).closeResource(context);
		} catch (Exception ex) {
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

		if (!isActive(extensionContext)) {
			return false;
		}

		if (parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
			return false;
		}
		return isSupportedType(parameterContext.getParameter().getType());
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {

		if (!hasConfiguration(context)) {
			return;
		}

		getClassFixtureContext(context).closeAll();
	}

	@Override
	public @Nullable Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		if (!isActive(context)) {
			return null;
		}

		Class<?> parameterType = parameterContext.getParameter().getType();
		try {
			FixtureResource resource = getOrCreateResource(context);
			return resolveValue(parameterType, resource);
		} catch (Exception ex) {
			throw new ParameterResolutionException(
					"Failed to resolve parameter of type " + parameterType.getName(), ex);
		}
	}

	@Override
	public <T> T interceptTestClassConstructor(Invocation<T> invocation,
			ReflectiveInvocationContext<Constructor<T>> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		return interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptBeforeAllMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptBeforeEachMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptTestMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptTestTemplateMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptAfterEachMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	@Override
	public void interceptAfterAllMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext)
			throws Throwable {

		interceptIfActive(invocation, extensionContext);
	}

	private static <T> T interceptIfActive(Invocation<T> invocation, ExtensionContext context) throws Throwable {
		if (!isActive(context)) {
			return invocation.proceed();
		}
		return EdtTestUtil.runInEdtAndGet(invocation::proceed, true);
	}

	private FixtureResource getOrCreateResource(ExtensionContext context) {
		return getClassFixtureContext(context).getOrCreateResource(context);
	}

	static boolean isActive(ExtensionContext context) {
		return findConfiguration(context) != null;
	}

	private static boolean hasConfiguration(ExtensionContext context) {

		if (context.getTestClass().isEmpty()) {
			return false;
		}
		if (context.getRequiredTestClass().isAnnotationPresent(CodeInsightFixtureTests.class)) {
			return true;
		}
		for (Method method : context.getRequiredTestClass().getDeclaredMethods()) {
			if (method.isAnnotationPresent(CodeInsightFixtureTests.class)) {
				return true;
			}
		}
		return false;
	}

	private static ClassFixtureContext getClassFixtureContext(ExtensionContext context) {
		ExtensionContext classContext = getClassContext(context);
		return classContext.getStore(NAMESPACE).computeIfAbsent(STORE_KEY,
				key -> new ClassFixtureContext(), ClassFixtureContext.class);
	}

	private static ExtensionContext getClassContext(ExtensionContext context) {

		ExtensionContext current = context;
		while (current.getTestMethod().isPresent() && current.getParent().isPresent()) {
			current = current.getParent().get();
		}
		return current;
	}

	private static FixtureResource createResource(ExtensionContext context) {

		CodeInsightFixtureTests configuration = getRequiredConfiguration(context);

		String fixtureName = configuration.fixtureName().isBlank()
				? defaultFixtureName(context)
				: configuration.fixtureName();

		IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
		TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createLightFixtureBuilder(fixtureName);
		IdeaProjectTestFixture ideaProjectFixture = builder.getFixture();
		CodeInsightTestFixture codeInsightFixture = factory.createCodeInsightFixture(ideaProjectFixture);

		try {
			EdtTestUtil.runInEdtAndWait(codeInsightFixture::setUp);
			return new FixtureResource(ideaProjectFixture, codeInsightFixture);
		} catch (Exception ex) {
			try {
				EdtTestUtil.runInEdtAndWait(codeInsightFixture::tearDown);
			} catch (Exception tearDownEx) {
				ex.addSuppressed(tearDownEx);
			}
			throw new ExtensionConfigurationException("Failed to create CodeInsightTestFixture", ex);
		}
	}

	private static String defaultFixtureName(ExtensionContext context) {

		String className = context.getRequiredTestClass().getSimpleName();
		return context.getTestMethod()
				.map(method -> className + "#" + method.getName())
				.orElse(className);
	}

	private void injectFields(Object testInstance, FixtureResource resource) {
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

	private Object resolveValue(Class<?> requestedType, FixtureResource resource) {
		if (requestedType.isInstance(resource.codeInsightFixture())) {
			return resource.codeInsightFixture();
		}
		if (requestedType.isInstance(resource.projectFixture())) {
			return resource.projectFixture();
		}
		throw new ExtensionConfigurationException(
				"Unsupported injection target type: " + requestedType.getName());
	}

	private static @Nullable CodeInsightFixtureTests findConfiguration(ExtensionContext context) {

		if (context.getTestClass().isEmpty()) {
			return null;
		}

		CodeInsightFixtureTests annotation = context.getTestMethod()
				.map(method -> method.getAnnotation(CodeInsightFixtureTests.class))
				.orElse(null);
		if (annotation != null) {
			return annotation;
		}

		return context.getRequiredTestClass().getAnnotation(CodeInsightFixtureTests.class);
	}

	private static CodeInsightFixtureTests getRequiredConfiguration(ExtensionContext context) {
		CodeInsightFixtureTests annotation = findConfiguration(context);
		if (annotation == null) {
			throw new ExtensionConfigurationException(
					"@" + CodeInsightFixtureTests.class.getSimpleName()
							+ " must be present on the test class or test method");
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

	private static class ClassFixtureContext {

		private final Map<String, FixtureResource> resources = new LinkedHashMap<>();

		private FixtureResource getOrCreateResource(ExtensionContext context) {
			return resources.computeIfAbsent(context.getUniqueId(),
					key -> createResource(context));
		}

		private @Nullable FixtureResource getResource(ExtensionContext context) {
			return resources.get(context.getUniqueId());
		}

		private void closeResource(ExtensionContext context) throws Exception {
			FixtureResource resource = resources.remove(context.getUniqueId());
			if (resource != null) {
				resource.close();
			}
		}

		private void closeAll() throws Exception {

			Exception failure = null;
			for (FixtureResource resource : new ArrayList<>(resources.values())) {
				try {
					resource.close();
				} catch (Exception ex) {
					if (failure == null) {
						failure = ex;
					} else {
						failure.addSuppressed(ex);
					}
				}
			}
			resources.clear();
			if (failure != null) {
				throw failure;
			}
		}

	}

	private record FixtureResource(
			IdeaProjectTestFixture projectFixture,
			CodeInsightTestFixture codeInsightFixture) {

		private void close() throws Exception {
			EdtTestUtil.runInEdtAndWait(codeInsightFixture::tearDown);
		}

	}

}
