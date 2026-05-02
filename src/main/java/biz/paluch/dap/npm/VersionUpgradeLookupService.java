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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * {@link VersionUpgradeLookupSupport} implementation for NPM
 * {@code package.json} dependency entries.
 *
 * <p>Resolves the {@link JsonStringLiteral} value beneath
 * {@code dependencies}/{@code devDependencies} into an
 * {@link ArtifactReference} by classifying the value through
 * {@link NpmPackageParser}. {@code Prefix} entries report a defined version so
 * suggestions can still be displayed even though no update is ever applied.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final NpmProjectContext buildContext;

	VersionUpgradeLookupService(Project project, NpmProjectContext buildContext) {
		super(project, buildContext);
		this.buildContext = buildContext;
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (buildContext.isAbsent()) {
			return ArtifactReference.unresolved();
		}

		JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(element);
		if (literal == null || !(literal.getParent() instanceof JsonProperty entry)) {
			return ArtifactReference.unresolved();
		}

		String name = entry.getName();
		if (!NpmPackageParser.NAME_ALLOWLIST.matcher(name).matches()) {
			return ArtifactReference.unresolved();
		}

		String raw = literal.getValue();
		NpmVersionExpression expression = NpmPackageParser.classify(raw);
		if (expression == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = NpmPackageParser.toArtifactId(name);
		return ArtifactReference.from(builder -> {
			builder.artifact(artifactId)
					.versionSource(VersionSource.declared(raw))
					.declarationElement(literal)
					.versionLiteral(literal);

			Dependency dependency = hasCachedState() ? getProjectState().findDependency(artifactId) : null;
			if (dependency != null) {
				builder.version(dependency.getCurrentVersion());
			} else {
				ArtifactVersion.from(extractCurrentVersionText(expression)).ifPresent(builder::version);
			}
		});
	}

	private static String extractCurrentVersionText(NpmVersionExpression expression) {

		return switch (expression) {
		case NpmVersionExpression.Exact exact -> exact.version();
		case NpmVersionExpression.RangeUpper range -> range.upper();
		case NpmVersionExpression.Alias alias -> extractCurrentVersionText(alias.inner());
		case NpmVersionExpression.Git git -> git.ref().committish();
		// Prefix has no concrete version; emitting empty avoids manufacturing one.
		case NpmVersionExpression.Prefix prefix -> "";
		};
	}

}
