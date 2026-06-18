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

package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlValue;

/**
 * TOML PSI locator for version-catalog declaration sites.
 *
 * @author Mark Paluch
 */
class TomlArtifactReferenceLocator {

	private final @Nullable ProjectState projectState;

	TomlArtifactReferenceLocator(@Nullable ProjectState projectState) {
		this.projectState = projectState;
	}

	public ArtifactReference locate(TomlLiteral literal) {

		TomlKeyValue keyValue = PsiTreeUtil.getParentOfType(literal, TomlKeyValue.class);
		if (keyValue == null) {
			return ArtifactReference.unresolved();
		}

		if (TomlParser.isInsideTable(literal, TomlParser.VERSIONS::equals)) {
			String propertyName = TomlParser.getTomlKeyName(keyValue.getKey());
			String rawVersion = TomlParser.getText(literal);
			if (StringUtils.isEmpty(propertyName)) {
				return ArtifactReference.unresolved();
			}
			return ArtifactReferenceUtils.resolve(propertyName, rawVersion, keyValue, literal, projectState);
		}

		if (TomlParser.isInsideTable(literal, it -> TomlParser.LIBRARIES.equals(it) || TomlParser.PLUGINS.equals(it))) {

			String keyName = TomlParser.getTomlKeyName(keyValue.getKey());
			boolean referencedVersion = TomlParser.VERSION_REF.equals(keyName);
			if (keyValue.getValue() != literal && !TomlParser.VERSION.equals(keyName)) {
				return ArtifactReference.unresolved();
			}

			TomlKeyValue declarationKeyValue = keyValue;
			if (TomlParser.VERSION.equals(keyName) || referencedVersion) {
				TomlInlineTable inlineTable = PsiTreeUtil.getParentOfType(literal, TomlInlineTable.class);
				if (inlineTable == null) {
					return ArtifactReference.unresolved();
				}
				declarationKeyValue = PsiTreeUtil.getParentOfType(inlineTable, TomlKeyValue.class);
				if (declarationKeyValue == null) {
					return ArtifactReference.unresolved();
				}
			}

			GradlePropertyResolver propertyResolver = GradlePropertyResolver
					.forFile(declarationKeyValue.getContainingFile());
			TomlParser.TomlCatalogDeclaration declaration = TomlParser.parseTomlEntry(declarationKeyValue,
					propertyResolver);
			if (!declaration.isComplete()) {
				return ArtifactReference.unresolved();
			}

			// A version.ref alias points at a [versions] entry; anchor the version on the
			// resolved [versions] literal so the alias is reported as a usage of that
			// property while still carrying its resolved coordinate and version.
			if (referencedVersion) {
				TomlValue resolvedLiteral = declaration.getRequiredVersionLiteral();
				ArtifactVersion resolvedVersion = ArtifactVersion.from(TomlParser.getText(resolvedLiteral))
						.orElse(null);
				if (resolvedVersion == null) {
					return ArtifactReference.unresolved();
				}

				ArtifactDeclaration artifact = declaration.toArtifactDeclaration(declarationKeyValue, resolvedLiteral);
				return ArtifactReference.from(artifact);
			}

			if (declaration.getVersionLiteral() != literal) {
				return ArtifactReference.unresolved();
			}

			return ArtifactReference.from(declaration.toArtifactDeclaration(declarationKeyValue, literal));
		}

		return ArtifactReference.unresolved();
	}

}
