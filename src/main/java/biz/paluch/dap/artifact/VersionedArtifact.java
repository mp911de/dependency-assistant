package biz.paluch.dap.artifact;

/**
 * Version state associated with artifact coordinates.
 *
 * <p>Implementations may be unversioned as defined by {@link Versioned}. The
 * {@link #of(ArtifactId, ArtifactVersion)} factory always creates a versioned
 * value.
 *
 * @author Mark Paluch
 * @see VersionedPackage
 */
public interface VersionedArtifact extends HasArtifactId, Versioned {

	/**
	 * Create a new versioned artifact.
	 * @param artifactId the artifact coordinates.
	 * @param version the version in use.
	 * @return a new versioned artifact.
	 */
	public static VersionedArtifact of(ArtifactId artifactId, ArtifactVersion version) {
		return new SimpleVersionedArtifact(artifactId, version);
	}

	/**
	 * Create a new versioned package given {@link PackageSystem}.
	 * @param packageSystem the package system.
	 * @return a new versioned package.
	 * @throws IllegalStateException if this artifact is unversioned.
	 */
	default VersionedPackage withPackageSystem(PackageSystem packageSystem) {
		return new SimpleVersionedPackage(PackageIdentity.of(getArtifactId(), packageSystem), getVersion());
	}

}
