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
import java.util.function.Supplier;

import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit Jupiter extension that registers project files for fixture-driven PSI
 * tests and resolves {@link PsiFile} parameters for the same method invocation.
 * <p>This extension supports {@link CodeInsightFixtureTests} and
 * {@link IdeaProjectTests}. Code Insight tests can use {@link ProjectFile} and
 * {@link EditorFile}; project tests can use {@link ProjectFile}.
 * <p>Resolution semantics mirror annotation intent: named lookup via
 * {@code @ProjectFile("name")} against the complete registry and positional
 * lookup for unannotated {@link PsiFile} parameters against project files in
 * declaration order.
 *
 * @author Mark Paluch
 * @see ProjectFile
 * @see EditorFile
 * @see CodeInsightFixtureExtension
 * @see ProjectExtension
 */
class ProjectFileExtension implements BeforeEachCallback, ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(ProjectFileExtension.class);

	private static final String FILES_KEY = "registeredFiles";

	private static final List<FileTypeMapping> FILE_TYPES = List.of(
			new FileTypeMapping(".gradle.kts", platformFileType("build.gradle.kts")),
			new FileTypeMapping(".gradle", platformFileType("build.gradle")),
			new FileTypeMapping(".kts", platformFileType("build.gradle.kts")),
			new FileTypeMapping(".properties", platformFileType("gradle.properties")),
			new FileTypeMapping(".versions.toml", platformFileType("libs.versions.toml")),
			new FileTypeMapping(".toml", platformFileType("libs.versions.toml")),
			new FileTypeMapping(".json", platformFileType("package.json")),
			new FileTypeMapping(".yaml", platformFileType("antora-playbook.yaml")),
			new FileTypeMapping(".yml", platformFileType("antora-playbook.yml")),
			new FileTypeMapping(".xml", () -> XmlFileType.INSTANCE));

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {

		if (!isActive(context)) {
			return;
		}

		Method testMethod = context.getRequiredTestMethod();
		ProjectFile[] projectFileAnnotations = testMethod.getAnnotationsByType(ProjectFile.class);
		EditorFile editorFile = testMethod.getAnnotation(EditorFile.class);
		Map<String, PsiFile> byName = new LinkedHashMap<>();
		List<PsiFile> projectFiles = new ArrayList<>();

		if (CodeInsightFixtureExtension.isActive(context)) {
			registerCodeInsightFiles(context, projectFileAnnotations, editorFile, byName, projectFiles);
		} else {
			registerProjectFiles(context, projectFileAnnotations, editorFile, byName, projectFiles);
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
		PsiFile file = lookup != null ? resolveByName(lookup, files) : resolveByPosition(parameterContext, files);

		Class<?> parameterType = parameterContext.getParameter().getType();
		if (!parameterType.isInstance(file)) {
			throw new ParameterResolutionException(
					"Registered file '%s' is %s, not %s"
							.formatted(file.getName(), file.getClass().getName(), parameterType.getName()));
		}
		return file;
	}

	private void registerCodeInsightFiles(ExtensionContext context, ProjectFile[] projectFileAnnotations,
			@Nullable EditorFile editorFile, Map<String, PsiFile> byName, List<PsiFile> projectFiles) {

		if (projectFileAnnotations.length == 0 && editorFile == null) {
			return;
		}

		CodeInsightTestFixture fixture = CodeInsightFixtureExtension.getFixture(context);
		EdtTestUtil.runInEdtAndWait(() -> {
			for (ProjectFile annotation : projectFileAnnotations) {
				String name = resolveName(annotation);
				validateUniqueFileName(byName, name);
				PsiFile psiFile = fixture.addFileToProject(name, annotation.content());
				byName.put(name, psiFile);
				projectFiles.add(psiFile);
			}
			if (editorFile != null) {
				validateUniqueFileName(byName, editorFile.name());
				PsiFile psiFile = fixture.configureByText(editorFile.name(), editorFile.content());
				byName.put(editorFile.name(), psiFile);
				projectFiles.add(psiFile);
			}
		});
	}

	private void registerProjectFiles(ExtensionContext context, ProjectFile[] projectFileAnnotations,
			@Nullable EditorFile editorFile, Map<String, PsiFile> byName, List<PsiFile> projectFiles) {

		if (editorFile != null) {
			throw new ExtensionConfigurationException(
					"@" + EditorFile.class.getSimpleName() + " requires @"
							+ CodeInsightFixtureTests.class.getName());
		}
		if (projectFileAnnotations.length == 0) {
			return;
		}

		Project project = ProjectExtension.getProject(context);
		MockVirtualFileSystem fileSystem = new MockVirtualFileSystem();
		String rootPath = rootPath(context);
		EdtTestUtil.runInEdtAndWait(() -> {
			for (ProjectFile annotation : projectFileAnnotations) {
				String name = resolveName(annotation);
				validateProjectFileName(byName, name);
				PsiFile psiFile = createPsiFile(project, fileSystem, rootPath, name, annotation.content());
				byName.put(name, psiFile);
				projectFiles.add(psiFile);
			}
		});
	}

	private static String rootPath(ExtensionContext context) {
		return "test-" + Integer.toUnsignedString(context.getUniqueId().hashCode());
	}

	private static PsiFile createPsiFile(Project project, MockVirtualFileSystem fileSystem, String rootPath,
			String name, String content) {

		resolveFileType(name);
		String path = rootPath + "/" + name;
		VirtualFile virtualFile = fileSystem.file(path, content).findFileByPath(path);

		PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
		if (psiFile == null) {
			throw new ExtensionConfigurationException("Failed to create PSI for @ProjectFile " + name);
		}
		return psiFile;
	}

	private static void validateProjectFileName(Map<String, PsiFile> byName, String name) {

		validateProjectRelativePath(name);
		validateUniqueFileName(byName, name);
		for (String registeredName : byName.keySet()) {
			if (registeredName.startsWith(name + "/")) {
				throw new ExtensionConfigurationException(
						"Cannot create @ProjectFile path below file: " + registeredName);
			}
			if (name.startsWith(registeredName + "/")) {
				throw new ExtensionConfigurationException("Cannot create @ProjectFile path below file: " + name);
			}
		}
	}

	private static void validateProjectRelativePath(String name) {

		if (name.startsWith("/") || name.endsWith("/") || name.contains("\\") || name.contains("..")
				|| name.contains("//") || name.contains(":")) {
			throw new ExtensionConfigurationException("@ProjectFile path must be project-relative: " + name);
		}
	}

	private static void validateUniqueFileName(Map<String, PsiFile> byName, String name) {

		if (byName.containsKey(name)) {
			throw new ExtensionConfigurationException("Duplicate @ProjectFile path: " + name);
		}
	}

	private static FileType resolveFileType(String name) {

		for (FileTypeMapping entry : FILE_TYPES) {
			if (name.endsWith(entry.suffix())) {
				FileType fileType = entry.fileType().get();
				if (fileType == FileTypes.UNKNOWN) {
					throw new ExtensionConfigurationException(
							"File type for @ProjectFile '%s' resolved to UNKNOWN".formatted(name));
				}
				return fileType;
			}
		}
		throw new ExtensionConfigurationException(
				"No file type registered for @ProjectFile '%s'; registered extensions: %s"
						.formatted(name, FILE_TYPES.stream().map(FileTypeMapping::suffix).toList()));
	}

	private static Supplier<FileType> platformFileType(String fileName) {
		return () -> FileTypeManager.getInstance().getFileTypeByFileName(fileName);
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

	private static boolean isActive(ExtensionContext context) {
		return CodeInsightFixtureExtension.isActive(context)
				|| ProjectExtension.isActive(context);
	}

	private record FileTypeMapping(String suffix, Supplier<FileType> fileType) {
	}

	private record RegisteredFiles(
			Map<String, PsiFile> byName,
			List<PsiFile> projectFiles) {
	}

}
