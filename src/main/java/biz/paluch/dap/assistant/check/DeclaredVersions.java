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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Versions and declaration styles for one artifact across its
 * {@link DeclarationSite declaration sites}.
 *
 * <p>The lowest declared version is the conservative upgrade baseline. Version
 * drift can occur across files or within one file. Declaration drift means that
 * sites mix inline versions and version properties.
 *
 * <p>The supplied sets are retained and exposed directly. Callers must not
 * modify them after construction. Factories order versions from highest to
 * lowest. Direct construction preserves the supplied order.
 *
 * @author Mark Paluch
 * @param versions the distinct parsed versions.
 * @param entries the version-drift display entries.
 * @param declarationEntries the declaration-style display entries.
 */
public record DeclaredVersions(Set<ArtifactVersion> versions, Set<VersionDrift> entries,
		Set<DeclarationDrift> declarationEntries) implements Sequence<ArtifactVersion> {

	private static final int MAX_DISPLAYED_VERSIONS = 5;

	private static final int MAX_DISPLAYED_FILES = 3;

	public static DeclaredVersions empty() {
		return new DeclaredVersions(Set.of(), Set.of(), Set.of());
	}

	/**
	 * Represent a single version without drift.
	 * <p>Persisted plans lack the declaration sites needed to report drift.
	 */
	public static DeclaredVersions of(ArtifactVersion version) {

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());
		versions.add(version);
		return new DeclaredVersions(versions, Set.of(), Set.of());
	}

	/**
	 * Merge distinct declared versions and drift entries.
	 */
	public static DeclaredVersions merge(Collection<DeclaredVersions> declaredVersions) {

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());
		Set<VersionDrift> entries = new TreeSet<>();
		Set<DeclarationDrift> declarationEntries = new TreeSet<>();
		for (DeclaredVersions declared : declaredVersions) {
			versions.addAll(declared.versions());
			entries.addAll(declared.entries());
			declarationEntries.addAll(declared.declarationEntries());
		}
		return new DeclaredVersions(versions, entries, declarationEntries);
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
	 * the project base path, or {@literal null} to retain absolute paths.
	 * @return the declared versions. The result is empty when no site carries a
	 * concrete dependency version.
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
			DeclaredDependency declaredDependency = site.dependency();
			// Resolve display paths here to keep path resolution out of EDT rendering.
			String location = getDisplayLocation(site.projectId(), site.file(), project);

			if (declaredDependency instanceof Dependency dependency) {
				ArtifactVersion version = dependency.getCurrentVersion();
				if (version instanceof GitRef gitRef) {
					ArtifactVersion resolved = gitRefResolver.apply(gitRef.getRef());
					if (resolved != null) {
						version = resolved;
					}
				}

				versions.add(version);
				entries.add(new VersionDrift(version, location));
			}

			for (VersionSource versionSource : declaredDependency.getVersionSources()) {
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
					ArtifactVersion.from(declared.getVersion())
							.ifPresent(declaredVersion -> {
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

	@Override
	public Iterator<ArtifactVersion> iterator() {
		return versions.iterator();
	}

	/**
	 * Return whether declaration sites report different versions.
	 */
	public boolean hasVersionDrift() {
		return versions().size() > 1 && entries.size() > 1;
	}

	/**
	 * Return whether sites mix inline versions and version properties.
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

	public boolean hasDrift() {
		return hasVersionDrift() || hasDeclarationDrift();
	}

	/**
	 * Visit recorded versions and their locations in display order.
	 * @param consumer receives version strings and precomputed display locations.
	 */
	public void forEachDrift(BiConsumer<String, String> consumer) {
		entries.forEach(it -> consumer.accept(it.version().toString(), it.location()));
	}

	public boolean hasVersion() {
		return !versions.isEmpty();
	}

	/**
	 * Return the highest declared version.
	 * @throws IllegalStateException if no version was found.
	 */
	public ArtifactVersion getHighestDeclaredVersion() {
		Assert.state(hasVersion(), "Cannot get declared version from empty versions");
		return versions.iterator().next();
	}

	/**
	 * Return the lowest declared version as the conservative upgrade baseline.
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
	 * Render a tooltip for conflicting declared versions.
	 * <p>Omit the current version because the table already shows it.
	 * @param currentVersion the version shown in the table.
	 * @return escaped HTML, or an empty chunk when there is no version drift.
	 */
	public HtmlChunk getVersionDriftToolTip(ArtifactVersion currentVersion) {

		if (!hasVersionDrift()) {
			return HtmlChunk.empty();
		}

		String current = currentVersion.toString();
		Map<String, List<String>> locationsByVersion = new LinkedHashMap<>();
		forEachDrift((version, file) -> {
			if (!current.equals(version)) {
				locationsByVersion.computeIfAbsent(version, key -> new ArrayList<>()).add(file);
			}
		});

		HtmlBuilder groups = new HtmlBuilder();
		int shown = 0;
		for (Map.Entry<String, List<String>> group : locationsByVersion.entrySet()) {

			if (shown == MAX_DISPLAYED_VERSIONS) {
				break;
			}
			groups.append(HtmlChunk.tag("li")
					.child(HtmlChunk.raw(MessageBundle.message("dialog.version-drift.tooltip.entry",
							HtmlChunk.text(group.getKey()).code(), renderLocations(group.getValue())))));
			shown++;
		}

		int overflow = locationsByVersion.size() - MAX_DISPLAYED_VERSIONS;
		if (overflow > 0) {
			groups.append(HtmlChunk.tag("li")
					.addText(MessageBundle.message("dialog.version-drift.tooltip.more.versions", overflow)));
		}

		return new HtmlBuilder()
				.append(HtmlChunk.tag("b").addText(MessageBundle.message("dialog.version-drift.tooltip.header")))
				.append(groups.wrapWith("ul")).toFragment();
	}

	/**
	 * Render a tooltip showing declaration styles and their locations.
	 * @return escaped HTML for the declaration drift.
	 */
	public HtmlChunk getDeclarationDriftToolTip() {

		Set<String> styles = new TreeSet<>();
		Set<String> files = new TreeSet<>();

		declarationEntries.forEach(it -> {
			styles.add(it.style.getName());
			files.add(it.location());
		});

		return new HtmlBuilder()
				.append(HtmlChunk.tag("b")
						.addText(MessageBundle.message("dialog.declaration-drift.tooltip.header") + ": "))
				.append(HtmlChunk.text(String.join(", ", styles)))
				.append(renderLocations(files)).toFragment();
	}

	private static HtmlChunk renderLocations(Collection<String> locations) {

		HtmlBuilder rendered = new HtmlBuilder();
		int shown = 0;
		for (String location : locations) {

			if (shown == MAX_DISPLAYED_FILES) {
				break;
			}
			rendered.append(HtmlChunk.tag("li").child(HtmlChunk.text(location).code()));
			shown++;
		}

		int overflow = locations.size() - MAX_DISPLAYED_FILES;
		if (overflow > 0) {
			rendered.append(HtmlChunk.tag("li")
					.addText(MessageBundle.message("dialog.drift.tooltip.other.files", overflow)));
		}

		return rendered.wrapWith("ul");
	}

	private static String getDisplayLocation(ProjectId projectId, VirtualFile file, @Nullable Project project) {

		if (StringUtils.hasText(projectId.groupId()) && StringUtils.hasText(projectId.artifactId())
				&& StringUtils.isEmpty(projectId.buildFile())) {
			return "%s:%s".formatted(projectId.groupId(), projectId.artifactId());
		}

		return project != null ? ProjectUtil.calcRelativeToProjectPath(file, project) : file.getPath();
	}

	/**
	 * A declared version and its precomputed display location.
	 */
	record VersionDrift(ArtifactVersion version, String location) implements Comparable<VersionDrift> {

		static final Comparator<VersionDrift> COMPARATOR = Comparator.comparing(VersionDrift::version)
				.thenComparing(VersionDrift::location, String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(VersionDrift o) {
			return COMPARATOR.compare(this, o);
		}

	}

	/**
	 * A declaration style and its precomputed display location.
	 */
	record DeclarationDrift(DeclarationStyle style, String location) implements Comparable<DeclarationDrift> {

		static final Comparator<DeclarationDrift> COMPARATOR = Comparator.comparing(DeclarationDrift::style)
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

		public @Nls String getName() {
			return MessageBundle.message(messageKey);
		}

	}

}
