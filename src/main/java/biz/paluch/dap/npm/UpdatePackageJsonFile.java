/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.npm;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileDependencyUpdater;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * PSI updater for NPM {@code package.json} dependency entries.
 *
 * <p>The updater delegates safe replacement rendering to the parsed
 * {@link NpmVersionExpression}. Prefix ranges and unsupported expressions are
 * not rewritten. Git declarations require a {@link GitVersion} target and
 * preserve the declared tag or SHA ref style. The surrounding property layout
 * and trailing comma remain unchanged. The writer does not append explanatory
 * comments because NPM package descriptors use JSON syntax.
 *
 * @author Mark Paluch
 * @see NpmVersionExpression
 */
class UpdatePackageJsonFile implements FileDependencyUpdater {

	private final JsonElementGenerator factory;

	UpdatePackageJsonFile(Project project) {
		this.factory = new JsonElementGenerator(project);
	}

	/**
	 * Apply matching dependency updates to the given {@code package.json} PSI file.
	 * Files with another PSI shape and entries outside the supported expression
	 * model remain unchanged.
	 *
	 * @param psiFile the {@code package.json} PSI file.
	 * @param updates the dependency updates to apply.
	 */
	@Override
	public void applyUpdates(PsiFile psiFile, DependencyUpdates updates) {

		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) {
			return;
		}

		for (String key : List.of("dependencies", "devDependencies")) {

			JsonProperty property = root.findProperty(key);
			if (property == null || !(property.getValue() instanceof JsonObject dependencies)) {
				continue;
			}

			for (JsonProperty entry : dependencies.getPropertyList()) {
				applyUpdates(entry, updates);
			}
		}
	}

	/**
	 * Apply a single update at the given anchor element.
	 * @param literal the anchor element, either the {@link JsonProperty} of a
	 * {@code dependencies} or {@code devDependencies} entry or an element nested
	 * within such a property.
	 * @param update the update to apply.
	 * @throws IllegalStateException when the anchor does not resolve to an
	 * enclosing {@link JsonProperty}.
	 */
	public void applyUpdate(PsiElement literal, DependencyUpdate update) {

		JsonProperty property = literal instanceof JsonProperty p ? p
				: PsiTreeUtil.getParentOfType(literal, JsonProperty.class);
		if (property == null) {
			throw new IllegalStateException(
					"Unsupported version literal element: %s".formatted(literal.getClass().getName()));
		}

		applyUpdates(property, DependencyUpdates.of(update));
	}

	private void applyUpdates(JsonProperty entry, DependencyUpdates updates) {

		String name = entry.getName();
		if (!NpmPackageParser.NAME_ALLOWLIST.matcher(name).matches()) {
			return;
		}

		if (!(entry.getValue() instanceof JsonStringLiteral literal)) {
			return;
		}

		NpmVersionExpression expression = NpmVersionExpression.parse(literal.getValue());
		if (expression == null) {
			return;
		}

		ArtifactId artifactId = expression.postProcess(NpmUtils.toArtifactId(name));

		updates.updateAll(entry.getContainingFile(), update -> {

			if (!artifactId.equals(update.artifactId())) {
				return;
			}

			String replacement = expression.renderUpdate(update.to());
			if (replacement != null && !replacement.equals(literal.getValue())) {
				JsonStringLiteral replacementLiteral = factory.createStringLiteral(replacement);
				literal.replace(replacementLiteral);
			}
		});
	}

}
