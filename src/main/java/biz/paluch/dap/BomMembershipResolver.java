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

package biz.paluch.dap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionedPackage;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Resolves missing Bill of Materials memberships from local build-tool storage.
 * <p>Existing memberships are retained. Unresolved memberships can be retried
 * on a later invocation. Only recent stable releases of known BOMs are
 * considered.
 * <p>Run on a background thread. The resolver supplies the read actions needed
 * by {@link DependencyAssistant#resolveBillOfMaterials}.
 *
 * @author Mark Paluch
 */
public class BomMembershipResolver {

	private static final int BACKFILL_MAX_AGE_YEARS = 3;

	private final Project project;

	private final List<DependencyAssistant> assistants;

	private final Cache cache;

	public BomMembershipResolver(Project project, List<DependencyAssistant> assistants, Cache cache) {
		this.project = project;
		this.assistants = assistants;
		this.cache = cache;
	}

	public static BomMembershipResolver create(Project project, Cache cache) {
		return new BomMembershipResolver(project, DependencyAssistantDispatcher.findAll(project), cache);
	}

	/**
	 * Resolve missing memberships for BOMs in the cache.
	 */
	public void resolveAll(ProgressIndicator indicator) {
		resolveMissingMemberships(cache.getCachedArtifacts(), indicator);
	}

	/**
	 * Resolve missing memberships for the given cached BOMs.
	 */
	public void resolve(Collection<PackageIdentity> artifactIds, ProgressIndicator indicator) {

		List<CachedArtifact> bomCandidates = new ArrayList<>(artifactIds.size());
		for (PackageIdentity pkg : artifactIds) {

			CachedArtifact cachedArtifact = cache.findCachedArtifact(pkg);
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

			if (!bomCandidate.isBom() || bomCandidate.getPackageSystem() == null) {
				continue;
			}

			for (Release release : bomCandidate.getReleases()) {

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

				BillOfMaterials bom = resolveBom(
						VersionedPackage.of(bomCandidate.toPackageIdentity(), release.version()));
				if (bom == null) {
					continue;
				}

				cache.putBillOfMaterials(bom);
			}
		}
	}

	private @Nullable BillOfMaterials resolveBom(VersionedPackage candidate) {

		for (DependencyAssistant assistant : assistants) {

			BillOfMaterials bom = ReadAction
					.compute(() -> assistant.resolveBillOfMaterials(project, candidate));
			if (bom != null) {
				return bom;
			}
		}
		return null;
	}

}
