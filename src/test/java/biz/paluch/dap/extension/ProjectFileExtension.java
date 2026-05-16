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
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit Jupiter extension that registers project files for fixture-driven PSI
 * tests.
 * <p>This extension processes {@link ProjectFile} and {@link EditorFile}
 * declarations on a test method, registers corresponding files with the active
 * {@link CodeInsightTestFixture}, and resolves {@link PsiFile} parameters for
 * the same method invocation.
 * <p>Resolution semantics mirror annotation intent: named lookup via
 * {@code @ProjectFile("name")} against the complete registry and positional
 * lookup for unannotated {@link PsiFile} parameters against project files in
 * declaration order.
 * <p>The extension is active only for test classes annotated with
 * {@link CodeInsightFixtureTests} and assumes that
 * {@link CodeInsightFixtureExtension} has already initialized the fixture.
 *
 * @author Mark Paluch
 * @see ProjectFile
 * @see EditorFile
 * @see CodeInsightFixtureExtension
 */
class ProjectFileExtension implements BeforeEachCallback, ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(ProjectFileExtension.class);

	private static final String FILES_KEY = "registeredFiles";

	@Override
	public void beforeEach(ExtensionContext context) {

		if (!isActive(context)) {
			return;
		}

		Method testMethod = context.getRequiredTestMethod();
		ProjectFile[] projectFileAnnotations = testMethod.getAnnotationsByType(ProjectFile.class);
		EditorFile editorFile = testMethod.getAnnotation(EditorFile.class);
		Map<String, PsiFile> byName = new LinkedHashMap<>();
		List<PsiFile> projectFiles = new ArrayList<>();

		if (projectFileAnnotations.length > 0 || editorFile != null) {
			CodeInsightTestFixture fixture = CodeInsightFixtureExtension.getFixture(context);
			EdtTestUtil.runInEdtAndWait(() -> {
				for (ProjectFile annotation : projectFileAnnotations) {
					validateMethodLevelProjectFile(annotation);
					String name = resolveName(annotation);
					PsiFile psiFile = fixture.addFileToProject(name, annotation.content());
					byName.put(name, psiFile);
					projectFiles.add(psiFile);
				}
				if (editorFile != null) {
					PsiFile psiFile = fixture.configureByText(editorFile.name(), editorFile.content());
					byName.put(editorFile.name(), psiFile);
					projectFiles.add(psiFile);
				}
			});
		}

		context.getStore(NAMESPACE).put(FILES_KEY, new RegisteredFiles(byName, projectFiles));
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		if (!isActive(extensionContext)) {
			return false;
		}
		if (parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
			return false;
		}
		return PsiFile.class.isAssignableFrom(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		RegisteredFiles files = extensionContext.getStore(NAMESPACE).get(FILES_KEY, RegisteredFiles.class);
		if (files == null) {
			throw new ParameterResolutionException(
					"No registered files found. Ensure beforeEach has run for this test");
		}

		Parameter parameter = parameterContext.getParameter();
		ProjectFile lookup = parameter.getAnnotation(ProjectFile.class);

		if (lookup != null) {
			return resolveByName(lookup, files);
		}

		return resolveByPosition(parameterContext, files);
	}

	private PsiFile resolveByName(ProjectFile lookup, RegisteredFiles files) {
		String name = resolveName(lookup);
		PsiFile file = files.byName().get(name);
		if (file == null) {
			throw new ParameterResolutionException(
					"No file registered under name '%s'; registered names: %s"
							.formatted(name, files.byName().keySet()));
		}
		return file;
	}

	private PsiFile resolveByPosition(ParameterContext parameterContext, RegisteredFiles files) {
		int positionalIndex = getFileIndex(parameterContext);
		List<PsiFile> projectFiles = files.projectFiles();
		if (positionalIndex >= projectFiles.size()) {
			throw new ParameterResolutionException(
					("Cannot resolve PsiFile at positional index %d; %d project file(s) registered. "
							+ "Use @ProjectFile(\"name\") for name-based lookup.")
									.formatted(positionalIndex, projectFiles.size()));
		}
		return projectFiles.get(positionalIndex);
	}

	private int getFileIndex(ParameterContext parameterContext) {
		Parameter[] parameters = parameterContext.getDeclaringExecutable().getParameters();
		int count = 0;
		for (int i = 0; i < parameterContext.getIndex(); i++) {
			Parameter p = parameters[i];
			if (PsiFile.class.isAssignableFrom(p.getType())
					&& p.getAnnotation(ProjectFile.class) == null) {
				count++;
			}
		}
		return count;
	}

	private static String resolveName(ProjectFile annotation) {
		String value = annotation.value();
		String name = annotation.name();
		if (StringUtils.hasText(value) && StringUtils.hasText(name) && !value.equals(name)) {
			throw new ExtensionConfigurationException(
					"@ProjectFile 'value' and 'name' must not both be set to different values: "
							+ "value='%s', name='%s'".formatted(value, name));
		}
		String resolved = StringUtils.isEmpty(value) ? name : value;
		if (StringUtils.isEmpty(resolved)) {
			throw new ExtensionConfigurationException(
					"@ProjectFile requires either 'value' or 'name' to be set");
		}
		return resolved;
	}

	private static void validateMethodLevelProjectFile(ProjectFile annotation) {
		resolveName(annotation); // validates name presence
	}

	private static boolean isActive(ExtensionContext context) {
		return context.getTestClass().isPresent()
				&& context.getRequiredTestClass().isAnnotationPresent(CodeInsightFixtureTests.class);
	}


	private record RegisteredFiles(Map<String, PsiFile> byName, List<PsiFile> projectFiles) {
	}

}
