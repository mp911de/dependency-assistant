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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Gradle Wrapper distribution declaration exposed as a
 * {@link VersionedDependencySite}.
 *
 * <p>The entry retains its {@link WrapperProperty} kind, source property and
 * value element, decoded version text, and {@code bin} or {@code all}
 * distribution flavor. The declaration uses the synthetic
 * {@code org.gradle:gradle} coordinate.
 *
 * @author Mark Paluch
 */
record GradleWrapperEntry(WrapperProperty property, Property propertyLiteral,
		PsiElement versionLiteral, String versionText, String flavor) implements VersionedDependencySite {

	boolean hasArtifactId(ArtifactId coordinate) {
		return property.artifactId().equals(coordinate);
	}

	@Nullable
	ArtifactVersion version() {
		return ArtifactVersion.from(versionText).orElse(null);
	}

	VersionSource versionSource() {
		return VersionSource.from(versionText());
	}

	@Override
	public DeclarationSource getDeclarationSource() {
		return DeclarationSource.dependency();
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
	public PackageSystem getPackageSystem() {
		return PackageSystem.OTHER;
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
