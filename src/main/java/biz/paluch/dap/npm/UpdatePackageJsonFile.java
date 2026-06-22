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

package biz.paluch.dap.npm;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * PSI updater for NPM {@code package.json} dependency entries.
 *
 * <p>
 * The updater dispatches on the {@link NpmVersionExpression} variant of the
 * original declaration and replaces only the variant's
 * {@link NpmVersionExpression#replaceableRange(String) replaceable range} via
 * the JSON PSI factory. JSON quoting style, whitespace, and trailing commas are
 * preserved by leaving the surrounding string-literal element intact and only
 * rewriting the literal's text. NPM JSON does not permit comments, so this
 * updater never appends explanatory metadata.
 *
 * @author Mark Paluch
 * @see NpmVersionExpression
 */
class UpdatePackageJsonFile {

	private final JsonElementGenerator factory;

	UpdatePackageJsonFile(Project project) {
		this.factory = new JsonElementGenerator(project);
	}

	/**
	 * Apply matching dependency updates to the given {@code package.json} PSI file.
	 * @param psiFile the {@code package.json} PSI file.
	 * @param updates the dependency updates to apply.
	 */
	public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

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
	 * Apply a single update at the given JSON string-literal anchor.
	 * @param literal the JSON string literal of a {@code dependencies} or
	 * {@code devDependencies} entry value.
	 * @param update the update to apply.
	 * @return {@literal true} if the rewrite was applied; {@literal false} on stale
	 * PSI without error.
	 * @throws IllegalStateException when the anchor resolves to an unexpected
	 * element kind.
	 */
	public void applyUpdate(PsiElement literal, DependencyUpdate update) {

		JsonProperty property = literal instanceof JsonProperty p ? p
				: PsiTreeUtil.getParentOfType(literal, JsonProperty.class);
		if (property == null) {
			throw new IllegalStateException(
					"Unsupported version literal element: %s".formatted(literal.getClass().getName()));
		}

		applyUpdates(property, List.of(update));
	}

	private void applyUpdates(JsonProperty entry, List<DependencyUpdate> updates) {

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

		ArtifactId artifactId = NpmPackageParser.toArtifactId(name);
		for (DependencyUpdate update : updates) {

			if (!artifactId.equals(update.coordinate())) {
				continue;
			}

			String replacement = render(expression, literal.getValue(), update);
			if (replacement != null && !replacement.equals(literal.getValue())) {
				JsonStringLiteral replacementLiteral = factory.createStringLiteral(replacement);
				literal.replace(replacementLiteral);
			}
			return;
		}
	}

	private static @Nullable String render(NpmVersionExpression expression, String rawValue, DependencyUpdate update) {
		return render(expression, rawValue, update.version());
	}

	static @Nullable String render(NpmVersionExpression expression, String rawValue,
			ArtifactVersion version) {

		if (!expression.isUpdatable()) {
			return null;
		}

		String tail = renderTail(expression, version);
		if (tail == null) {
			return null;
		}

		int start = expression.replaceableRange(rawValue).getStartOffset();
		int end = expression.replaceableRange(rawValue).getEndOffset();
		return rawValue.substring(0, start) + tail + rawValue.substring(end);
	}

	private static @Nullable String renderTail(NpmVersionExpression expression,
			ArtifactVersion version) {

		return switch (expression) {
		case NpmVersionExpression.Git git -> renderGitTail(git, version);
		case NpmVersionExpression.Alias alias -> renderTail(alias.inner(), version);
		case NpmVersionExpression.Range range -> renderTail(range.upper(), version);
		default -> version.toString();
		};
	}

	private static @Nullable String renderGitTail(NpmVersionExpression.Git git,
			ArtifactVersion version) {

		if (!(version instanceof GitVersion gitVersion)) {
			return null;
		}
		RefStyle style = RefStyle.from(git.ref().committish().text());
		return gitVersion.renderRef(style, git.ref().committish().text());
	}


}
