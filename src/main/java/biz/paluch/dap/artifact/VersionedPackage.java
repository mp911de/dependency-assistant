package biz.paluch.dap.artifact;

/**
 * A versioned package.
 *
 * @author Mark Paluch
 * @see VersionedArtifact
 */
public interface VersionedPackage extends VersionedArtifact, HasPackageSystem, HasPackageIdentity {

	/**
	 * Create a new versioned package.
	 * @param pkg the package coordinates.
	 * @param version the version in use.
	 * @return a new versioned package.
	 */
	public static VersionedPackage of(PackageIdentity pkg, ArtifactVersion version) {
		return new SimpleVersionedPackage(pkg, version);
	}

	@Override
	default PackageSystem getPackageSystem() {
		return getPackageIdentity().getPackageSystem();
	}

}
