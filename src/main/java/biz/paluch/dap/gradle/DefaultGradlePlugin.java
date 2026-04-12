package biz.paluch.dap.gradle;

import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;

/**
 * Default {@link GradlePlugin} implementation.
 * 
 * @author Mark Paluch
 */
class DefaultGradlePlugin implements GradlePlugin {

	private final ArtifactId id;

	public DefaultGradlePlugin(ArtifactId id) {
		this.id = id;
	}

	@Override
	public String groupId() {
		return id.groupId();
	}

	@Override
	public String artifactId() {
		return id.artifactId();
	}

	@Override
	public int compareTo(ArtifactId o) {
		return id.compareTo(o);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if ((!(o instanceof ArtifactId other))) {
			return false;
		}
		return Objects.equals(id, other);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
