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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Gradle wrapper distribution URL.
 *
 * @author Mark Paluch
 */
record GradleWrapperEntry(WrapperProperty property, PropertyImpl propertyLiteral,
		PropertyValueImpl versionLiteral, String versionText, String flavor) implements VersionedDependencySite {

	boolean hasArtifactId(ArtifactId coordinate) {
		return property.artifactId().equals(coordinate);
	}

	@Nullable
	ArtifactVersion version() {
		return ArtifactVersion.from(versionText).orElse(null);
	}

	VersionSource versionSource() {
		return StringUtils.hasText(versionText) ? VersionSource.declared(versionText) : VersionSource.none();
	}

	@Override
	public ArtifactVersion getVersion() {
		return version();
	}

	@Override
	public PsiElement getVersionElement() {
		return versionLiteral;
	}

	@Override
	public ArtifactId getArtifactId() {
		return property.artifactId();
	}

	@Override
	public VersionSource getVersionSource() {
		return versionSource();
	}

	@Override
	public PsiElement getDeclarationElement() {
		return versionLiteral;
	}

}
