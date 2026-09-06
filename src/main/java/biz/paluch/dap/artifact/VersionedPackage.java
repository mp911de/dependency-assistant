package biz.paluch.dap.artifact;

/**
 * Version state associated with a {@link PackageIdentity}.
 *
 * <p>Implementations may be unversioned as defined by {@link Versioned}. The
 * {@link #of(PackageIdentity, ArtifactVersion)} factory always creates a
 * versioned value.
 *
 * @author Mark Paluch
 * @see VersionedArtifact
 */
public interface VersionedPackage extends VersionedArtifact, HasPackageSystem, HasPackageIdentity {

	public static VersionedPackage of(PackageIdentity pkg, ArtifactVersion version) {
		return new SimpleVersionedPackage(pkg, version);
	}

	@Override
	default PackageSystem getPackageSystem() {
		return getPackageIdentity().getPackageSystem();
	}

}
