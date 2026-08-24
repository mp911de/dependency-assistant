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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PomLocator;
import biz.paluch.dap.artifact.VersionedArtifact;
import biz.paluch.dap.metadata.IssueTracker;
import biz.paluch.dap.metadata.Platform;
import biz.paluch.dap.metadata.RepositoryConnection;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.HttpClientUtil;
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
 * Extracts project metadata from an artifact's POM and parent POM chain.
 *
 * <p>Repository and issue-tracker metadata may be inherited from a parent POM.
 * Project name and description are taken only from the artifact POM. POMs are
 * obtained through registered {@link PomLocator} extensions without network
 * access, and traversal stops at a cycle or {@link #MAX_CHAIN_DEPTH}.
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
	 * Create an introspector resolving POMs through the registered
	 * {@link PomLocator} extensions.
	 *
	 * @param project the project used to locate and read POM files.
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
	 * Inspect the POM chain of the given artifact.
	 *
	 * @param artifactId the artifact to inspect.
	 * @param inUseVersion the artifact version identifying the first POM in the
	 * chain.
	 * @param indicator the cancellation indicator for traversal.
	 * @return the extracted metadata, or a nothing-found marker when no POM in
	 * reach carries usable metadata.
	 */
	@RequiresBackgroundThread
	public CachedMetadata getProjectMetadata(ArtifactId artifactId, ArtifactVersion inUseVersion,
			ProgressIndicator indicator) {

		Findings findings = ReadAction
				.nonBlocking(() -> inspectChain(VersionedArtifact.of(artifactId, inUseVersion), indicator))
				.executeSynchronously();
		String projectName = findings.projectName;
		String projectDescription = findings.projectDescription;

		return CachedMetadata.of(findings.repositoryUrl, findings.issueTrackerUrl, projectName, projectDescription);
	}

	private Findings inspectChain(VersionedArtifact artifact, ProgressIndicator indicator) {

		String repositoryUrl = null;
		String issueTrackerUrl = null;
		String projectName = null;
		String description = null;
		RepositoryConnection repositoryConnection = null;

		List<XmlFile> chain = new ArrayList<>();
		inspectChain(artifact, indicator, chain::add);

		if (chain.isEmpty()) {
			return Findings.NONE;
		}

		MavenPomProperties propertyResolver = MavenPomProperties.from(chain, xmlFile -> {
			MavenDomProjectModel domModel = MavenDomUtil.getMavenDomModel(xmlFile,
					MavenDomProjectModel.class);
			return domModel != null
					? new MavenBomParser.DomPropertyResolver(xmlFile, domModel)
					: MavenPomProperties.from(xmlFile);
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

	private void inspectChain(VersionedArtifact artifact, ProgressIndicator indicator,
			Consumer<XmlFile> pomFileConsumer) {

		Set<VersionedArtifact> visited = new HashSet<>();
		visited.add(artifact);

		VirtualFile pomFile = findPom(artifact.getArtifactId(), artifact.getVersion().toString());

		for (int depth = 0; pomFile != null && depth < MAX_CHAIN_DEPTH; depth++) {

			indicator.checkCanceled();

			VersionedArtifact parent = doWithRoot(psiManager.findFile(pomFile), it -> {

				pomFileConsumer.accept((XmlFile) it.getContainingFile());

				XmlTag parentTag = it.findFirstSubTag("parent");
				if (parentTag != null) {
					PomTag pomTag = PomTag.of(parentTag);
					String parentArtifactId = pomTag.getArtifactId();
					String parentGroupId = pomTag.getGroupId();
					ArtifactVersion parentVersion = ArtifactVersion.from(pomTag.getText("version")).orElse(null);
					if (StringUtils.hasText(parentGroupId) && StringUtils.hasText(parentArtifactId)
							&& parentVersion != null) {
						return VersionedArtifact.of(ArtifactId.of(parentGroupId, parentArtifactId), parentVersion);
					}
				}
				return null;
			});

			if (parent == null || !visited.add(parent)) {
				break;
			}

			pomFile = findPom(parent.getArtifactId(), parent.getVersion().toString());
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
				facts.issueManagementUrl = HttpClientUtil.isBrowsable(issueUrl) ? issueUrl : null;
				facts.issueManagementSystem = Subtag.of(issueManagement, "system").getText(resolver);
			}

			return facts;
		});
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
		 * Return the declared SCM repository candidates in selection order.
		 *
		 * @return the available non-empty candidates ordered as {@code url},
		 * {@code connection}, then {@code developerConnection}.
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

}
