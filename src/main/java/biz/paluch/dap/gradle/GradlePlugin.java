package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;

import org.springframework.util.Assert;

/**
 * Extension to {@link ArtifactId} representing a Gradle plugin.
 * <p>Plugins use a single identifier that is used for {@link #artifactId()} and
 * {@link #groupId()}.
 * @author Mark Paluch
 */
interface GradlePlugin extends ArtifactId {

	/**
	 * Create a new plugin {@link ArtifactId}.
	 * @param id the plugin identifier.
	 * @return the created {@link ArtifactId} for {@code id};
	 */
	static GradlePlugin of(String id) {
		return new DefaultGradlePlugin(ArtifactId.of(id, id));
	}

	/**
	 * Return whether the given {@link ArtifactId} is a Gradle plugin (i.e. whether
	 * {@link #groupId()} and {@link #artifactId()} are equal.
	 * @param id the artifact to check.
	 * @return {@code true} if the {@link ArtifactId} represents a plugin.
	 */
	static boolean isPlugin(ArtifactId id) {
		return id.artifactId().equals(id.groupId());
	}

	/**
	 * Return a {@code GradlePlugin} from the given {@link ArtifactId}.
	 * <p>Returns either a casted version or creates a new {@link GradlePlugin}
	 * instance. Must be {@link #isPlugin(ArtifactId)}.
	 * @param id the identifier.
	 * @return the GradlePlugin object.
	 * @throws IllegalArgumentException if the given {@code id} is not a
	 * {@link #isPlugin(ArtifactId)}.
	 */
	static GradlePlugin from(ArtifactId id) {
		if (id instanceof GradlePlugin plugin) {
			return plugin;
		}
		Assert.isTrue(isPlugin(id), "ArtifactId is not a plugin");
		return new DefaultGradlePlugin(id);
	}

	/**
	 * Return the plugin Id.
	 * @see #groupId()
	 */
	default String id() {
		return groupId();
	}


}
