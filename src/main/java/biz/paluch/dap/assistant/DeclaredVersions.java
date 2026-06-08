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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * The distinct versions an artifact is declared at across its
 * {@link DeclarationSite declaration sites}.
 *
 * <p>The set drives both the upgrade baseline
 * ({@link #getHighestDeclaredVersion()}) and drift reporting: drift exists when
 * the sites declare the artifact at more than one distinct version, whether
 * across files or twice within one file.
 *
 * @author Mark Paluch
 * @param versions the distinct parsed versions found in the declaration sites.
 * @param entries the sortable declaration entries used for conflict display.
 * @see UpdateCandidate
 */
record DeclaredVersions(Set<ArtifactVersion> versions, Set<VersionDrift> entries) {

	/**
	 * Return an empty result with no declared versions.
	 *
	 * @return a result with no versions and no declaration entries.
	 */
	public static DeclaredVersions none() {
		return new DeclaredVersions(Set.of(), Set.of());
	}

	/**
	 * Collect the declared versions across the given dependency sites.
	 *
	 * <p>Git references are resolved through the supplied resolver before they are
	 * compared. A resolver result of {@literal null} leaves the original
	 * {@link GitRef} in the version set.
	 *
	 * @param declarationSites the declaration sites to inspect.
	 * @param gitRefResolver the resolver used to translate Git refs into known
	 * artifact versions.
	 * @return the declared versions; empty when no site carries a concrete
	 * dependency version.
	 */
	public static DeclaredVersions from(Collection<DeclarationSite> declarationSites,
			Function<String, @Nullable ArtifactVersion> gitRefResolver) {

		if (declarationSites.isEmpty()) {
			return none();
		}

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());
		Set<VersionDrift> entries = new TreeSet<>();
		for (DeclarationSite site : declarationSites) {
			if (site.dependency() instanceof Dependency dependency) {
				ArtifactVersion version = dependency.getCurrentVersion();
				if (version instanceof GitRef gitRef) {
					ArtifactVersion resolved = gitRefResolver.apply(gitRef.getRef());
					if (resolved != null) {
						version = resolved;
					}
				}

				versions.add(version);
				entries.add(new VersionDrift(site.projectId(), site.file(), version));
			}
		}
		return new DeclaredVersions(versions, entries);
	}

	/**
	 * Return whether the declared versions disagree.
	 *
	 * @return {@literal true} if the inline declared versions differ;
	 * {@literal false} otherwise.
	 */
	boolean hasConflict() {
		return versions().size() > 1 && entries.size() > 1;
	}

	/**
	 * Visit each conflicting declaration in display order.
	 *
	 * <p>When all display locations share the same project prefix, the prefix is
	 * omitted from the location passed to the consumer.
	 *
	 * @param consumer the consumer receiving version strings and display locations.
	 */
	public void forEachConflict(BiConsumer<String, String> consumer) {

		List<String> locations = new ArrayList<>();
		for (VersionDrift entry : entries) {
			locations.add(entry.getDeclarationLocation());
		}

		String sharedPrefix = extractSharedPrefix(locations);

		entries.forEach(it -> {
			String location = it.getDeclarationLocation();
			if (sharedPrefix != null && location.startsWith(sharedPrefix)) {
				location = location.substring(sharedPrefix.length());
			}
			consumer.accept(it.version.toString(), location);
		});
	}

	private @Nullable String extractSharedPrefix(List<String> locations) {
		boolean allHaveSingleColon = locations.stream()
				.allMatch(loc -> loc.chars().filter(c -> c == ':').count() == 1);

		if (!allHaveSingleColon || locations.isEmpty()) {
			return null;
		}

		String firstPrefix = locations.getFirst().substring(0, locations.getFirst().indexOf(':') + 1);
		boolean allHaveSamePrefix = locations.stream().allMatch(loc -> loc.startsWith(firstPrefix));
		return allHaveSamePrefix ? firstPrefix : null;
	}

	/**
	 * Return whether any concrete version was found.
	 *
	 * @return {@literal true} if at least one version was found; {@literal false}
	 * otherwise.
	 */
	boolean hasVersion() {
		return !versions.isEmpty();
	}

	/**
	 * Return the highest version the artifact is declared at, used as the upgrade
	 * baseline.
	 *
	 * @return the first version according to the artifact version ordering.
	 * @throws IllegalStateException if no version was found.
	 */
	public ArtifactVersion getHighestDeclaredVersion() {
		Assert.state(hasVersion(), "Cannot get declared version from empty versions");
		return versions.iterator().next();
	}

	/**
	 * Render the tool tip text.
	 */
	public String getToolTipText() {

		StringBuilder tooltip = new StringBuilder();

		tooltip.append("<h3>")
				.append(MessageBundle.message("dialog.conflict.tooltip.header"))
				.append("</h3>");
		tooltip.append("<ul>");

		forEachConflict((version, file) -> {
			tooltip.append("<li>")
					.append(MessageBundle.message("dialog.conflict.tooltip.entry", "<code>" + version + "</code>",
							file))
					.append("</li>");
		});
		tooltip.append("</ul>");

		return tooltip.toString();
	}

	/**
	 * Display entry for one drift declaration.
	 *
	 * @param projectId the project identity associated with the declaration.
	 * @param file the file containing the declaration.
	 * @param version the parsed or resolved declared version.
	 */
	record VersionDrift(ProjectId projectId, VirtualFile file, ArtifactVersion version)
			implements Comparable<VersionDrift> {

		static Comparator<VersionDrift> COMPARATOR = Comparator.comparing(VersionDrift::version)
				.thenComparing(VersionDrift::getDeclarationLocation, String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(VersionDrift o) {
			return COMPARATOR.compare(this, o);
		}

		/**
		 * Return the project or file location shown to users.
		 *
		 * @return the Maven coordinate when available, or the build file name
		 * otherwise.
		 */
		public String getDeclarationLocation() {

			if (StringUtils.hasText(projectId.groupId()) && StringUtils.hasText(projectId.artifactId())
					&& StringUtils.isEmpty(projectId.buildFile())) {
				return "%s:%s".formatted(projectId.groupId(), projectId.artifactId());
			}

			return file.getPresentableName();
		}

		/**
		 * Return the display label for this drift entry.
		 *
		 * @return the declaration location followed by the version.
		 */
		public String toDisplayString() {
			return "%s: %s".formatted(getDeclarationLocation(), version);
		}

	}

}
