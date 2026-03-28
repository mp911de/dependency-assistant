/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.mavenupdater.artifact;

import java.util.Objects;

/**
 * Where the version comes from.
 */
public abstract class VersionSource {

	private static final NoVersionSource NONE = new NoVersionSource();

	public static VersionSource none() {
		return NONE;
	}

	public static VersionSource property(String version) {
		return new VersionPropertySource(version);
	}

	public static VersionSource declared(String version) {
		return new DeclaredVersion(version);
	}

	public static VersionSource declared(DeclarationSource version) {
		return new VersionDeclarationSource(version);
	}

	public static VersionSource profileProperty(String profile, String property) {
		return new ProfilePropertySource(property, profile);
	}

	public static final class NoVersionSource extends VersionSource {

		@Override
		public String toString() {
			return "none";
		}
	}

	public static final class VersionDeclarationSource extends VersionSource {

		private final DeclarationSource declarationSource;

		public VersionDeclarationSource(DeclarationSource declarationSource) {
			this.declarationSource = declarationSource;
		}

		public DeclarationSource getDeclarationSource() {
			return declarationSource;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionDeclarationSource that)) {
				return false;
			}
			return Objects.equals(declarationSource, that.declarationSource);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(declarationSource);
		}
	}

	public static final class DeclaredVersion extends VersionSource {

		private final String version;

		public DeclaredVersion(String version) {
			this.version = version;
		}

		public String getVersion() {
			return version;
		}

		@Override
		public String toString() {
			return version;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof DeclaredVersion that)) {
				return false;
			}
			return Objects.equals(version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(version);
		}
	}

	public static class VersionPropertySource extends VersionSource {

		private final String property;

		public VersionPropertySource(String property) {
			this.property = property;
		}

		public String getProperty() {
			return property;
		}

		@Override
		public String toString() {
			return "${" + property + '}';
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionPropertySource that)) {
				return false;
			}
			return Objects.equals(getClass(), that.getClass()) && Objects.equals(property, that.property);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(property);
		}
	}

	public static final class ProfilePropertySource extends VersionPropertySource {

		private final String profileId;

		public ProfilePropertySource(String property, String profileId) {
			super(property);
			this.profileId = profileId;
		}

		public String getProfileId() {
			return profileId;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof ProfilePropertySource that)) {
				return false;
			}
			if (!super.equals(o)) {
				return false;
			}
			return Objects.equals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 31 + Objects.hashCode(profileId);
		}
	}

}
