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
 * Updates NPM dependency literals while preserving surrounding JSON layout.
 * <p>Prefix ranges and unsupported expressions remain unchanged. Git
 * declarations require a {@link GitVersion} target and retain their tag or SHA
 * ref style.
 *
 * @author Mark Paluch
 */
class UpdatePackageJsonFile implements FileDependencyUpdater {

	private final JsonElementGenerator factory;

	UpdatePackageJsonFile(Project project) {
		this.factory = new JsonElementGenerator(project);
	}

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
	 * Update a dependency anchored at a property or one of its children.
	 * @throws IllegalStateException if the anchor has no enclosing
	 * {@link JsonProperty}.
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
