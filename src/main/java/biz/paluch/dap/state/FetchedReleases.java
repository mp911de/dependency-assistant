package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import org.jspecify.annotations.Nullable;

/**
 * Value object capturing the release fetching for a single artifact.
 *
 * @author Mark Paluch
 */
public class FetchedReleases implements HasArtifactId {

	private final ArtifactId artifactId;

	private final Collection<CachedRelease> releases;

	private final FetchPlan plan;

	private final @Nullable String preferredSource;

	private final Collection<String> emptySources;

	/**
	 * Create a new {@code FetchedReleases} instance.
	 * @param artifactId the artifact identifier for which the releases were
	 * fetched.
	 * @param releases cached releases.
	 * @param plan the underlying fetch plan.
	 * @param preferredSource the preferred source for the artifact, can either
	 * contain {@link ReleaseSource#getId()} or be empty (or {@literal null}).
	 * @param emptySources the {@link ReleaseSource#getId() release source
	 * identifiers} that returned no releases.
	 */
	public FetchedReleases(ArtifactId artifactId, Collection<CachedRelease> releases, FetchPlan plan,
			@Nullable String preferredSource, Collection<String> emptySources) {
		this.artifactId = artifactId;
		this.releases = releases;
		this.plan = plan;
		this.preferredSource = preferredSource;
		this.emptySources = emptySources;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public Collection<CachedRelease> getReleases() {
		return this.releases;
	}

	public boolean isFullFetch() {
		return this.plan.isFullFetch();
	}

	public @Nullable String getPreferredSource() {
		return this.preferredSource;
	}

	public Collection<String> getEmptySources() {
		return this.emptySources;
	}

	/**
	 * Convert the given {@link Release}s to {@link CachedRelease}s.
	 * @param releases iterable of releases to convert.
	 * @return the resulting list of cached releases.
	 */
	public static List<CachedRelease> convert(Iterable<? extends Release> releases) {
		List<CachedRelease> converted = new ArrayList<>();
		for (Release release : releases) {
			converted.add(CachedRelease.from(release));
		}
		return converted;
	}

}
