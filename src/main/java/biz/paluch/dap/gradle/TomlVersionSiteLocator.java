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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.DirectCoordinate;
import biz.paluch.dap.gradle.GradleVersionSite.MapPropertyVersion;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;

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
			if (keyValue.getValue() != literal && !TomlParser.VERSION.equals(keyName)) {
				return GradleVersionSite.absent();
			}

			TomlKeyValue declarationKeyValue = keyValue;
			if (TomlParser.VERSION.equals(keyName)) {
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
			if (!declaration.isComplete() || declaration.versionLiteral() != literal) {
				return GradleVersionSite.absent();
			}

			DependencySite site = declaration.toDependencySite(declarationKeyValue, literal);
			return classifyTomlEntry(site, declarationKeyValue, literal);
		}

		return GradleVersionSite.absent();
	}

	private static GradleVersionSite classifyTomlEntry(DependencySite site, PsiElement declaration,
			PsiElement versionElement) {

		ArtifactId id = site.getArtifactId();
		VersionSource source = site.getVersionSource();
		ArtifactVersion version = GradleVersionSite.versionOf(site);

		if (source instanceof VersionSource.VersionProperty property) {
			return new MapPropertyVersion(id, property.getProperty(), source, declaration, versionElement, version);
		}

		return new DirectCoordinate(id, source, declaration, versionElement, version);
	}

}
