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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
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
 * across files or twice within one file. Declaration drift exists when the
 * sites mix inline versions and version properties.
 *
 * @author Mark Paluch
 * @param versions the distinct parsed versions found in the declaration sites.
 * @param entries the sortable declaration entries used for conflict display.
 * @param declarationEntries the sortable declaration-style entries used for
 * drift display.
 * @see UpgradeCandidate
 */
public record DeclaredVersions(Set<ArtifactVersion> versions, Set<VersionDrift> entries,
		Set<DeclarationDrift> declarationEntries) {

	/** Maximum number of version groups listed in the version-drift tool tip. */
	private static final int MAX_DISPLAYED_VERSIONS = 5;

	/** Maximum number of locations listed per drift group before overflow. */
	private static final int MAX_DISPLAYED_FILES = 3;

	/**
	 * Return an empty result with no declared versions.
	 *
	 * @return a result with no versions and no declaration entries.
	 */
	public static DeclaredVersions empty() {
		return new DeclaredVersions(Set.of(), Set.of(), Set.of());
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
	 * @param project the project used to render declaration locations relative to
	 * the project base path; can be {@literal null}.
	 * @return the declared versions; empty when no site carries a concrete
	 * dependency version.
	 */
	public static DeclaredVersions from(Collection<DeclarationSite> declarationSites,
			Function<String, @Nullable ArtifactVersion> gitRefResolver, @Nullable Project project) {

		if (declarationSites.isEmpty()) {
			return empty();
		}

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());
		Set<VersionDrift> entries = new TreeSet<>();
		Set<DeclarationDrift> declarationEntries = new TreeSet<>();

		boolean containsGitVersion = hasGitVersion(declarationSites);
		for (DeclarationSite site : declarationSites) {
			if (!(site.dependency() instanceof Dependency dependency)) {
				continue;
			}

			String location = getDisplayLocation(site.projectId(), site.file(), project);
			ArtifactVersion version = dependency.getCurrentVersion();
			if (version instanceof GitRef gitRef) {
				ArtifactVersion resolved = gitRefResolver.apply(gitRef.getRef());
				if (resolved != null) {
					version = resolved;
				}
			}

			versions.add(version);
			entries.add(new VersionDrift(version, location));

			for (VersionSource versionSource : dependency.getVersionSources()) {
				if (versionSource instanceof VersionSource.DeclaredVersion declared) {
					declarationEntries.add(new DeclarationDrift(DeclarationStyle.INLINE, location));

					if (containsGitVersion) {
						ArtifactVersion resolved = gitRefResolver.apply(declared.getVersion());
						if (resolved != null) {
							versions.add(resolved);
							entries.add(new VersionDrift(resolved, location));
							continue;
						}
					}
					ArtifactVersion.from(declared.getVersion()).ifPresent(declaredVersion -> {
						versions.add(declaredVersion);
						entries.add(new VersionDrift(declaredVersion, location));
					});
				} else if (versionSource.isProperty()) {
					declarationEntries.add(new DeclarationDrift(DeclarationStyle.PROPERTY, location));
				}
			}
		}
		return new DeclaredVersions(versions, entries, declarationEntries);
	}

	private static boolean hasGitVersion(Collection<DeclarationSite> declarationSites) {
		boolean hasGitRefs = false;
		for (DeclarationSite site : declarationSites) {
			if (site.dependency() instanceof Dependency dependency
					&& (dependency.getCurrentVersion() instanceof GitRef
							|| dependency.getCurrentVersion() instanceof GitVersion)) {
				hasGitRefs = true;
				break;
			}
		}
		return hasGitRefs;
	}

	/**
	 * Return whether the declared versions disagree.
	 *
	 * @return {@literal true} if the inline declared versions differ;
	 * {@literal false} otherwise.
	 */
	public boolean hasVersionDrift() {
		return versions().size() > 1 && entries.size() > 1;
	}

	/**
	 * Return whether the declaration styles disagree.
	 *
	 * @return {@literal true} when the declaration sites mix inline versions and
	 * version properties; {@literal false} otherwise.
	 */
	public boolean hasDeclarationDrift() {

		boolean inline = false;
		boolean property = false;
		for (DeclarationDrift entry : declarationEntries) {
			if (entry.style == DeclarationStyle.INLINE) {
				inline = true;
			}
			if (entry.style == DeclarationStyle.PROPERTY) {
				property = true;
			}

			if (property && inline) {
				break;
			}
		}
		return inline && property;
	}

	/**
	 * Return whether either version values or declaration styles drift.
	 */
	public boolean hasDrift() {
		return hasVersionDrift() || hasDeclarationDrift();
	}

	/**
	 * Visit each conflicting declaration in display order.
	 *
	 * <p>File locations are rendered relative to the project base path when a
	 * project is available.
	 *
	 * @param consumer the consumer receiving version strings and display locations.
	 */
	public void forEachDrift(BiConsumer<String, String> consumer) {
		entries.forEach(it -> consumer.accept(it.version().toString(), it.location()));
	}

	/**
	 * Visit each declaration-style drift entry in display order.
	 *
	 * <p>File locations are rendered relative to the project base path when a
	 * project is available.
	 *
	 * @param consumer the consumer receiving style labels and display locations.
	 */
	public void forEachDeclarationDrift(BiConsumer<String, String> consumer) {
		declarationEntries.forEach(it -> consumer.accept(MessageBundle.message(it.style().messageKey), it.location()));
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
	 * Return the lowest version the artifact is declared at, the most conservative
	 * declared version across all sites.
	 *
	 * @return the last version according to the artifact version ordering.
	 * @throws IllegalStateException if no version was found.
	 */
	public ArtifactVersion getLowestDeclaredVersion() {
		Assert.state(hasVersion(), "Cannot get declared version from empty versions");
		if (versions instanceof SequencedCollection<?> s) {
			return (ArtifactVersion) s.getLast();
		}

		return new ArrayList<>(versions).getLast();
	}


	/**
	 * Render the version-drift tool tip text.
	 *
	 * <p>Declarations are grouped by their declared version, ordered by version. Up
	 * to {@link #MAX_DISPLAYED_VERSIONS} version groups are listed, each naming up
	 * to {@link #MAX_DISPLAYED_FILES} locations before collapsing the remainder
	 * into an overflow count. The {@code currentVersion} group is omitted because
	 * it is already shown in the current-version column.
	 *
	 * @param currentVersion the version shown in the current-version column,
	 * excluded from the listed groups.
	 * @return the version-drift tool tip markup; an empty string when no version
	 * drift exists.
	 */
	public String getVersionDriftToolTipText(ArtifactVersion currentVersion) {

		if (!hasVersionDrift()) {
			return "";
		}

		String current = currentVersion.toString();
		Map<String, List<String>> locationsByVersion = new LinkedHashMap<>();
		forEachDrift((version, file) -> {
			if (!current.equals(version)) {
				locationsByVersion.computeIfAbsent(version, key -> new ArrayList<>()).add(file);
			}
		});

		StringBuilder tooltip = new StringBuilder("<b>")
				.append(MessageBundle.message("dialog.version-drift.tooltip.header"))
				.append("</b><ul>");

		int shown = 0;
		for (Map.Entry<String, List<String>> group : locationsByVersion.entrySet()) {
			if (shown == MAX_DISPLAYED_VERSIONS) {
				break;
			}
			tooltip.append("<li>")
					.append(MessageBundle.message("dialog.version-drift.tooltip.entry",
							"<code>" + group.getKey() + "</code>", renderLocations(group.getValue())))
					.append("</li>");
			shown++;
		}

		int overflow = locationsByVersion.size() - MAX_DISPLAYED_VERSIONS;
		if (overflow > 0) {
			tooltip.append("<li>")
					.append(MessageBundle.message("dialog.version-drift.tooltip.more.versions", overflow))
					.append("</li>");
		}

		return tooltip.append("</ul>").toString();
	}

	/**
	 * Render the declaration-drift tool tip text.
	 *
	 * <p>Declarations are grouped by their declaration style. Both styles are
	 * always listed, each naming up to {@link #MAX_DISPLAYED_FILES} locations
	 * before collapsing the remainder into an overflow count.
	 *
	 * @return the declaration-drift tool tip markup; an empty string when no
	 * declaration drift exists.
	 */
	public String getDeclarationDriftToolTipText() {

		Set<String> styles = new TreeSet<>();
		Set<String> files = new TreeSet<>();
		forEachDeclarationDrift((style, file) -> {
			styles.add(style);
			files.add(file);
		});

		StringBuilder builder = new StringBuilder("<b>")
				.append(MessageBundle.message("dialog.declaration-drift.tooltip.header"))
				.append(": </b> ")
				.append(org.springframework.util.StringUtils.collectionToDelimitedString(styles, ", "));

		builder.append(renderLocations(files));

		return builder.toString();
	}

	/**
	 * Render a comma-separated list of up to {@link #MAX_DISPLAYED_FILES}
	 * declaration locations, collapsing any remaining locations into an overflow
	 * count.
	 *
	 * @param locations the locations to render, in display order.
	 * @return the rendered location markup.
	 */
	private static String renderLocations(Collection<String> locations) {

		int shown = Math.min(locations.size(), MAX_DISPLAYED_FILES);
		StringBuilder rendered = new StringBuilder();

		rendered.append("<ul>");
		int count = 0;
		for (String location : locations) {
			if (count++ > shown) {
				break;
			}
			rendered.append("<li>");
			rendered.append("<code>").append(location).append("</code>");
			rendered.append("</li>");
		}

		int overflow = locations.size() - MAX_DISPLAYED_FILES;
		if (overflow > 0) {
			rendered.append("<li>").append(MessageBundle.message("dialog.drift.tooltip.other.files", overflow))
					.append("</li>");
		}

		rendered.append("</ul>");
		return rendered.toString();
	}

	/**
	 * Compute the declaration location.
	 *
	 * <p>Resolved eagerly at construction because rendering a path relative to the
	 * project base path is a slow operation that must not run on the EDT. Maven
	 * coordinates take precedence; otherwise the file path is rendered relative to
	 * the project base path, or as an absolute path when no project is available.
	 *
	 * @param projectId the project identity associated with the declaration.
	 * @param file the file containing the declaration.
	 * @param project the project used for base-path resolution; can be
	 * {@literal null}.
	 * @return the declaration location, never {@literal null}.
	 */
	private static String getDisplayLocation(ProjectId projectId, VirtualFile file, @Nullable Project project) {

		if (StringUtils.hasText(projectId.groupId()) && StringUtils.hasText(projectId.artifactId())
				&& StringUtils.isEmpty(projectId.buildFile())) {
			return "%s:%s".formatted(projectId.groupId(), projectId.artifactId());
		}

		return project != null ? ProjectUtil.calcRelativeToProjectPath(file, project) : file.getPath();
	}

	/**
	 * Display entry for one drift declaration.
	 *
	 * @param version the parsed or resolved declared version.
	 * @param location the declaration location shown to users, resolved at
	 * construction.
	 */
	record VersionDrift(ArtifactVersion version, String location) implements Comparable<VersionDrift> {

		static Comparator<VersionDrift> COMPARATOR = Comparator.comparing(VersionDrift::version)
				.thenComparing(VersionDrift::location, String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(VersionDrift o) {
			return COMPARATOR.compare(this, o);
		}

	}

	/**
	 * Display entry for one declaration-style drift location.
	 *
	 * @param style the declaration style seen at the location.
	 * @param location the declaration location shown to users, resolved at
	 * construction.
	 */
	record DeclarationDrift(DeclarationStyle style, String location) implements Comparable<DeclarationDrift> {

		static Comparator<DeclarationDrift> COMPARATOR = Comparator.comparing(DeclarationDrift::style)
				.thenComparing(DeclarationDrift::location, String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(DeclarationDrift o) {
			return COMPARATOR.compare(this, o);
		}

	}

	private enum DeclarationStyle {

		INLINE("dialog.declaration-drift.tooltip.inline"),

		PROPERTY("dialog.declaration-drift.tooltip.property");

		private final String messageKey;

		DeclarationStyle(String messageKey) {
			this.messageKey = messageKey;
		}

	}

}
