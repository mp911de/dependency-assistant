package biz.paluch.dap.artifact;

/**
 * Simple {@link VersionedArtifact} implementation.
 *
 * @author Mark Paluch
 */
record SimpleVersionedArtifact(ArtifactId artifactId, ArtifactVersion version) implements VersionedArtifact {

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
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
