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

package biz.paluch.dap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * Resolves missing Bill of Materials memberships from local build-tool storage
 * through the registered {@link DependencyAssistant assistants} and stores them
 * in the {@link Cache}.
 *
 * <p>Only artifacts already carrying at least one cached membership are
 * considered BOMs. Candidate versions are the non-preview, non-snapshot
 * releases newer than three years that lack a membership. Resolved memberships
 * are immutable and cached forever; unresolvable versions carry no persisted
 * state and are simply retried on the next invocation.
 *
 * <p>Resolution parses BOM POMs and must run on a background thread; each
 * assistant call is wrapped in its own short read action.
 *
 * @author Mark Paluch
 * @see DependencyAssistant#resolveBillOfMaterials
 */
public class BomMembershipResolver {

	private static final int BACKFILL_MAX_AGE_YEARS = 3;

	private final Project project;

	private final List<DependencyAssistant> assistants;

	private final Cache cache;

	/**
	 * Create a resolver dispatching over the given assistants.
	 *
	 * @param project the project providing repository configuration.
	 * @param assistants the assistants consulted in registration order.
	 * @param cache the cache providing memberships and receiving resolved ones.
	 */
	public BomMembershipResolver(Project project, List<DependencyAssistant> assistants, Cache cache) {
		this.project = project;
		this.assistants = assistants;
		this.cache = cache;
	}

	/**
	 * Create a resolver over the assistants registered for the given project.
	 *
	 * @param project the project providing repository configuration.
	 * @param cache the cache providing memberships and receiving resolved ones.
	 * @return the resolver.
	 */
	public static BomMembershipResolver create(Project project, Cache cache) {
		return new BomMembershipResolver(project, DependencyAssistantDispatcher.findAll(project), cache);
	}

	/**
	 * Sweep the whole cache and resolve missing memberships for every BOM artifact.
	 *
	 * @param indicator the progress indicator to report cancellation through.
	 */
	public void resolveAll(ProgressIndicator indicator) {
		resolveMissingMemberships(cache.getCachedArtifacts(), indicator);
	}

	/**
	 * Resolve missing memberships for the given artifacts only. Artifacts without a
	 * cache entry or without cached membership are skipped.
	 *
	 * @param artifactIds the artifacts of interest.
	 * @param indicator the progress indicator to report cancellation through.
	 */
	public void resolve(Collection<ArtifactId> artifactIds, ProgressIndicator indicator) {

		List<CachedArtifact> bomCandidates = new ArrayList<>(artifactIds.size());
		for (ArtifactId artifactId : artifactIds) {

			CachedArtifact cachedArtifact = cache.findCachedArtifact(artifactId);
			if (cachedArtifact != null && cachedArtifact.getPackageSystem() != null) {
				bomCandidates.add(cachedArtifact);
			}
		}

		resolveMissingMemberships(bomCandidates, indicator);
	}

	private void resolveMissingMemberships(List<CachedArtifact> bomCandidates, ProgressIndicator indicator) {

		LocalDateTime cutoff = LocalDateTime.ofInstant(Instant.ofEpochMilli(cache.now()), ZoneOffset.UTC)
				.minusYears(BACKFILL_MAX_AGE_YEARS);

		for (CachedArtifact bomCandidate : bomCandidates) {

			if (!bomCandidate.hasBoms() || bomCandidate.getPackageSystem() == null) {
				continue;
			}

			for (Release release : bomCandidate.getVersionOptions()) {

				indicator.checkCanceled();

				if (release.isPreview() || release.isSnapshotVersion()) {
					continue;
				}

				LocalDateTime releaseDate = release.releaseDate();
				if (releaseDate == null || releaseDate.isBefore(cutoff)) {
					continue;
				}

				if (bomCandidate.hasBom(release.version())) {
					continue;
				}

				Map<ArtifactId, ArtifactVersion> members = resolveBom(bomCandidate.toPackageIdentity(),
						release.version());
				if (members.isEmpty()) {
					continue;
				}

				cache.putBillOfMaterials(BillOfMaterials.of(bomCandidate, release.version(), members),
						bomCandidate.getPackageSystem());
			}
		}
	}

	private Map<ArtifactId, ArtifactVersion> resolveBom(PackageIdentity pkg, ArtifactVersion version) {

		for (DependencyAssistant assistant : assistants) {

			Map<ArtifactId, ArtifactVersion> members = ReadAction
					.compute(() -> assistant.resolveBillOfMaterials(project, pkg, version));
			if (!members.isEmpty()) {
				return members;
			}
		}
		return Map.of();
	}

}
