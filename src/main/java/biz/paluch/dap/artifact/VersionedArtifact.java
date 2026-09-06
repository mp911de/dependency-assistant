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

	public static VersionedArtifact of(ArtifactId artifactId, ArtifactVersion version) {
		return new SimpleVersionedArtifact(artifactId, version);
	}

	/**
	 * Associate this versioned artifact with a package ecosystem.
	 * @throws IllegalStateException if this artifact is unversioned.
	 */
	default VersionedPackage withPackageSystem(PackageSystem packageSystem) {
		return new SimpleVersionedPackage(PackageIdentity.of(getArtifactId(), packageSystem), getVersion());
	}

}
