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
import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.CoordinateSite;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.util.PsiTreeUtil;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlValue;

/**
 * TOML PSI locator for version-catalog declaration sites.
 *
 * @author Mark Paluch
 */
class TomlVersionSiteLocator implements VersionSiteLocator<TomlLiteral> {

	@Override
	public GradleVersionSite locate(TomlLiteral literal) {

		TomlKeyValue keyValue = PsiTreeUtil.getParentOfType(literal, TomlKeyValue.class);
		if (keyValue == null) {
			return GradleVersionSite.absent();
		}

		if (TomlParser.isInsideTable(literal, TomlParser.VERSIONS::equals)) {
			String propertyName = TomlParser.getTomlKeyName(keyValue.getKey());
			String rawVersion = TomlParser.getText(literal);
			if (StringUtils.isEmpty(propertyName)) {
				return GradleVersionSite.absent();
			}
			return new BackingProperty(propertyName, rawVersion, keyValue, literal);
		}

		if (TomlParser.isInsideTable(literal, it -> TomlParser.LIBRARIES.equals(it) || TomlParser.PLUGINS.equals(it))) {

			String keyName = TomlParser.getTomlKeyName(keyValue.getKey());
			boolean referencedVersion = TomlParser.VERSION_REF.equals(keyName);
			if (keyValue.getValue() != literal && !TomlParser.VERSION.equals(keyName)) {
				return GradleVersionSite.absent();
			}

			TomlKeyValue declarationKeyValue = keyValue;
			if (TomlParser.VERSION.equals(keyName) || referencedVersion) {
				TomlInlineTable inlineTable = PsiTreeUtil.getParentOfType(literal, TomlInlineTable.class);
				if (inlineTable == null) {
					return GradleVersionSite.absent();
				}
				declarationKeyValue = PsiTreeUtil.getParentOfType(inlineTable, TomlKeyValue.class);
				if (declarationKeyValue == null) {
					return GradleVersionSite.absent();
				}
			}

			GradlePropertyResolver propertyResolver = GradlePropertyResolver
					.forFile(declarationKeyValue.getContainingFile());
			TomlParser.TomlDependencyDeclaration declaration = TomlParser.parseTomlEntry(declarationKeyValue,
					propertyResolver);
			if (!declaration.isComplete()) {
				return GradleVersionSite.absent();
			}

			// A version.ref alias points at a [versions] entry; anchor the version on the
			// resolved [versions] literal so the alias is reported as a usage of that
			// property while still carrying its resolved coordinate and version.
			if (referencedVersion) {
				TomlValue resolvedLiteral = declaration.getRequiredVersionLiteral();
				ArtifactVersion resolvedVersion = ArtifactVersion.from(TomlParser.getText(resolvedLiteral))
						.orElse(null);
				if (resolvedVersion == null) {
					return GradleVersionSite.absent();
				}
				DependencySite site = declaration.toDependencySite(declarationKeyValue, resolvedLiteral);
				return new CoordinateSite(site.getArtifactId(), site.getVersionSource(), site.getDeclarationSource(),
						declarationKeyValue, resolvedLiteral, resolvedVersion);
			}

			if (declaration.versionLiteral() != literal) {
				return GradleVersionSite.absent();
			}

			DependencySite site = declaration.toDependencySite(declarationKeyValue, literal);
			return new CoordinateSite(site.getArtifactId(), site.getVersionSource(), site.getDeclarationSource(),
					declarationKeyValue, literal, GradleVersionSite.versionOf(site));
		}

		return GradleVersionSite.absent();
	}

}
