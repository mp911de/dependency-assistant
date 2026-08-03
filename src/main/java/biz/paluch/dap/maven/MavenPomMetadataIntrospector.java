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

package biz.paluch.dap.maven;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PomLocator;
import biz.paluch.dap.metadata.IssueTracker;
import biz.paluch.dap.metadata.Platform;
import biz.paluch.dap.metadata.RepositoryConnection;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedBom;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jspecify.annotations.Nullable;

/**
 * Introspector for Maven POM files to extract project metadata.
 *
 * @author Mark Paluch
 * @see PomLocator
 */
class MavenPomMetadataIntrospector extends MavenPomSupport {

	static final int MAX_CHAIN_DEPTH = 10;

	private final Project project;

	private final Cache cache;

	private final BetterPsiManager psiManager;

	/**
	 * Create an inspector resolving POMs through the registered {@link PomLocator}
	 * extensions.
	 */
	public MavenPomMetadataIntrospector(Project project) {
		this(project, StateService.getInstance(project).getCache());
	}

	MavenPomMetadataIntrospector(Project project, Cache cache) {
		this.project = project;
		this.cache = cache;
		this.psiManager = BetterPsiManager.getInstance(project);
	}

	/**
	 * Inspect the POM chain of the given artifact and store the resulting project
	 * metadata on the artifact's cache entry.
	 * @param artifactId the artifact to inspect.
	 * @param inUseVersion the currently used version probed first, or
	 * {@literal null} to probe the cached release versions only.
	 * @return the stored metadata; the nothing-found marker when no POM in reach
	 * carries usable metadata.
	 */
	@RequiresBackgroundThread
	public CachedMetadata getProjectMetadata(ArtifactId artifactId, ArtifactVersion inUseVersion,
			ProgressIndicator indicator) {

		Findings findings = ReadAction.nonBlocking(() -> inspectChain(artifactId, inUseVersion.toString(), indicator))
				.executeSynchronously();
		String projectName = findings.projectName;
		String projectDescription = findings.projectDescription;
		if (findings.isEmpty()) {
			findings = crossCheck(artifactId, indicator);
		}

		return CachedMetadata.of(findings.repositoryUrl, findings.issueTrackerUrl, projectName, projectDescription);
	}

	/**
	 * Inspect the chain of a single deterministic same-groupId BOM candidate: any
	 * persisted membership relation establishes the project connection, regardless
	 * of version.
	 */
	private Findings crossCheck(ArtifactId artifactId, ProgressIndicator indicator) {

		Gav candidate = findGoverningBom(artifactId);
		if (candidate == null) {
			candidate = findManagedMember(artifactId);
		}
		if (candidate == null) {
			return Findings.NONE;
		}

		Gav candidateToUse = candidate;

		return ReadAction.nonBlocking(() -> inspectChain(ArtifactId.of(candidateToUse.groupId(),
				candidateToUse.artifactId()), candidateToUse.version(), indicator)).executeSynchronously();
	}

	// return
	/**
	 * The release tag is captured only from the artifact's own POM at the given
	 * version; the project name from the artifact's own POM at any probed version.
	 */
	private Findings inspectChain(ArtifactId artifactId, String version, ProgressIndicator indicator) {

		String repositoryUrl = null;
		String issueTrackerUrl = null;
		String projectName = null;
		String description = null;
		RepositoryConnection repositoryConnection = null;

		List<XmlFile> chain = new ArrayList<>();
		inspectChain(artifactId, version, indicator, chain::add);

		if (chain.isEmpty()) {
			return Findings.NONE;
		}

		MavenPomProperties propertyResolver = MavenPomProperties.combine(chain, xmlFile -> {
			MavenDomProjectModel domModel = MavenDomUtil.getMavenDomModel(xmlFile,
					MavenDomProjectModel.class);
			return domModel != null
					? new MavenBomParser.DomPropertyResolver(xmlFile, domModel)
					: new MavenProjectMetadataPropertyResolver(xmlFile);
		});

		boolean first = true;
		for (XmlFile xmlFile : chain) {

			indicator.checkCanceled();

			PomFacts facts = readPom(xmlFile, propertyResolver);
			if (facts == null) {
				break;
			}

			if (first) {
				projectName = facts.projectName;
				description = facts.description;
			}
			first = false;
			if (repositoryUrl == null) {

				for (String candidate : facts.getRepositoryCandidates()) {
					RepositoryConnection connection = Platform.findConnection(candidate, null);
					if (connection != null) {
						repositoryConnection = connection;
						repositoryUrl = connection.getUrl();
						break;
					}
				}
			}
			if (issueTrackerUrl == null) {
				if (StringUtils.hasText(facts.issueManagementUrl)) {
					issueTrackerUrl = facts.issueManagementUrl;
				} else if (repositoryConnection != null) {
					IssueTracker issueTracker = Platform.findIssueTracker(repositoryConnection,
							facts.issueManagementSystem);
					if (issueTracker != null) {
						issueTrackerUrl = issueTracker.getBaseUrl().toString();
					}
				}
			}
			if (repositoryUrl != null && issueTrackerUrl != null) {
				break;
			}
		}

		return new Findings(projectName, description, repositoryUrl, issueTrackerUrl);
	}

	private void inspectChain(ArtifactId artifactId, String version, ProgressIndicator indicator,
			Consumer<XmlFile> pomFileConsumer) {

		Set<Gav> visited = new HashSet<>();
		visited.add(Gav.of(artifactId, version));

		VirtualFile pomFile = findPom(artifactId, version);

		for (int depth = 0; pomFile != null && depth < MAX_CHAIN_DEPTH; depth++) {

			indicator.checkCanceled();

			Gav parentGav = doWithRoot(psiManager.findFile(pomFile), it -> {

				pomFileConsumer.accept((XmlFile) it.getContainingFile());

				XmlTag parent = it.findFirstSubTag("parent");
				if (parent != null) {
					PomTag pomTag = PomTag.of(parent);
					String parentArtifactId = pomTag.getArtifactId();
					String parentGroupId = pomTag.getGroupId();
					String parentVersion = pomTag.getText("version");
					if (StringUtils.hasText(parentGroupId) && StringUtils.hasText(parentArtifactId)
							&& StringUtils.hasText(parentVersion)) {
						return new Gav(parentGroupId, parentArtifactId, parentVersion);
					}
				}
				return null;
			});

			if (parentGav == null || !visited.add(parentGav)) {
				break;
			}

			pomFile = findPom(ArtifactId.of(parentGav.groupId(), parentGav.artifactId()), parentGav.version());
		}
	}

	/**
	 * Locate the POM file for the given coordinates through the registered
	 * {@link PomLocator} extensions. Overridable in tests to serve fixture POMs
	 * without extension-point registration.
	 * @param artifactId the artifact coordinates.
	 * @param version the artifact version.
	 * @return the POM file, or {@literal null} if no locator finds it.
	 */
	protected @Nullable VirtualFile findPom(ArtifactId artifactId, String version) {
		return PomLocator.findPom(project, artifactId, version);
	}

	private @Nullable PomFacts readPom(XmlFile file, PropertyResolver resolver) {

		return doWithRoot(file, it -> {

			PomTag root = PomTag.of(it);
			PomFacts facts = new PomFacts();
			facts.projectName = root.getText(NAME, resolver);
			facts.description = root.getText("description", resolver);

			XmlTag scm = it.findFirstSubTag(SCM);
			if (scm != null) {
				PomTag scmTag = PomTag.of(scm);
				facts.scmUrl = scmTag.getText(SCM_URL, resolver);
				facts.scmConnection = scmTag.getText(SCM_CONNECTION, resolver);
				facts.scmDeveloperConnection = scmTag.getText(SCM_DEVELOPER_CONNECTION, resolver);
			}

			XmlTag issueManagement = it.findFirstSubTag(ISSUE_MANAGEMENT);
			if (issueManagement != null) {
				String issueUrl = Subtag.of(issueManagement, SCM_URL).getText(resolver);
				facts.issueManagementUrl = isAllowedBrowserUrl(issueUrl) ? issueUrl : null;
				facts.issueManagementSystem = Subtag.of(issueManagement, "system").getText(resolver);
			}

			return facts;
		});
	}

	private static boolean isAllowedBrowserUrl(@Nullable String value) {

		if (!StringUtils.hasText(value)) {
			return false;
		}

		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Find the first same-groupId BOM whose persisted membership lists the member,
	 * at the newest such membership's version.
	 */
	private @Nullable Gav findGoverningBom(ArtifactId member) {

		List<CachedArtifact> boms = new ArrayList<>();
		for (CachedArtifact candidate : cache.getCachedArtifacts()) {
			if (candidate.hasBoms() && member.groupId().equals(candidate.getGroupId())
					&& candidate.getArtifactId() != null && !member.artifactId().equals(candidate.getArtifactId())) {
				boms.add(candidate);
			}
		}
		boms.sort(Comparator.comparing(CachedArtifact::artifactId));

		for (CachedArtifact bom : boms) {

			List<CachedBom> memberships = bom.getBomMemberships();
			for (int i = memberships.size() - 1; i >= 0; i--) {

				CachedBom membership = memberships.get(i);
				if (!membership.isMember(member)) {
					continue;
				}

				ArtifactVersion version = membership.getVersion();
				return new Gav(bom.groupId(), bom.artifactId(), version.toString());
			}
		}

		return null;
	}

	/**
	 * Find the first same-groupId member of the inspected BOM artifact, taken from
	 * its newest persisted membership.
	 */
	private @Nullable Gav findManagedMember(ArtifactId bomId) {

		CachedArtifact bom = cache.findCachedArtifact(bomId);
		if (bom == null || !bom.hasBoms()) {
			return null;
		}

		List<CachedBom> memberships = bom.getBomMemberships();
		if (memberships.isEmpty()) {
			return null;
		}

		CachedBom membership = memberships.getLast();
		CachedBom.CachedBomMember first = null;
		for (CachedBom.CachedBomMember member : membership.getMembers()) {

			String memberArtifactId = member.getArtifactId();
			if (!bomId.groupId().equals(member.getGroupId()) || bomId.artifactId()
					.equals(memberArtifactId)) {
				continue;
			}
			if (first == null || memberArtifactId.compareTo(first.getArtifactId()) < 0) {
				first = member;
			}
		}

		if (first == null) {
			return null;
		}

		return Gav.of(first.toArtifactId(), first.getVersion());
	}

	/**
	 * Interpolated metadata facts read from one POM.
	 */
	static class PomFacts {

		@Nullable
		String projectName;

		@Nullable
		String description;

		@Nullable
		String scmUrl;

		@Nullable
		String scmConnection;

		@Nullable
		String scmDeveloperConnection;

		@Nullable
		String issueManagementUrl;

		@Nullable
		String issueManagementSystem;

		/**
		 * The declared SCM repository candidates in selection order: {@code url} over
		 * {@code connection} over {@code developerConnection}.
		 */
		List<String> getRepositoryCandidates() {

			List<String> candidates = new ArrayList<>(3);
			if (StringUtils.hasText(scmUrl)) {
				candidates.add(scmUrl);
			}
			if (StringUtils.hasText(scmConnection)) {
				candidates.add(scmConnection);
			}
			if (StringUtils.hasText(scmDeveloperConnection)) {
				candidates.add(scmDeveloperConnection);
			}
			return candidates;
		}

	}

	private static class Findings {

		static final Findings NONE = new Findings(null, null, null, null);

		private final @Nullable String projectName;

		private final @Nullable String projectDescription;

		private final @Nullable String repositoryUrl;

		private final @Nullable String issueTrackerUrl;

		Findings(@Nullable String projectName, @Nullable String projectDescription, @Nullable String repositoryUrl,
				@Nullable String issueTrackerUrl) {
			this.repositoryUrl = repositoryUrl;
			this.issueTrackerUrl = issueTrackerUrl;
			this.projectName = projectName;
			this.projectDescription = projectDescription;
		}

		boolean isEmpty() {
			return repositoryUrl == null && issueTrackerUrl == null;
		}

	}

	record Gav(String groupId, String artifactId, String version) {

		static Gav of(ArtifactId artifactId, String version) {
			return new Gav(artifactId.groupId(), artifactId.artifactId(), version);
		}

		static Gav of(ArtifactId artifactId, ArtifactVersion version) {
			return new Gav(artifactId.groupId(), artifactId.artifactId(), version.toString());
		}

	}

}
