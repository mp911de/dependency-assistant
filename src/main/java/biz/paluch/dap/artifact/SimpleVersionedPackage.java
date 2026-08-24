package biz.paluch.dap.artifact;

/**
 * Simple {@link VersionedPackage} implementation.
 *
 * @author Mark Paluch
 */
record SimpleVersionedPackage(PackageIdentity pkg, ArtifactVersion version) implements VersionedPackage {

	@Override
	public ArtifactId getArtifactId() {
		return pkg.getArtifactId();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return pkg;
	}

	@Override
	public PackageSystem getPackageSystem() {
		return pkg.getPackageSystem();
	}

	@Override
	public boolean isVersioned() {
		return true;
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

}
