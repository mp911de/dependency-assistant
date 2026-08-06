package biz.paluch.dap.artifact;

/**
 * A versioned artifact.
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
	 */
	default VersionedPackage withPackageSystem(PackageSystem packageSystem) {
		return new SimpleVersionedPackage(PackageIdentity.of(getArtifactId(), packageSystem), getVersion());
	}

}
