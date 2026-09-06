/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.artifact;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.util.Assert;

/**
 * An optional artifact version.
 * <p>Check {@link #isVersioned()} before accessing the version, or use
 * {@link #map(Function)} and {@link #orElseGet(Supplier)} to handle absence.
 *
 * @author Mark Paluch
 */
public interface Versioned extends VersionAware {

	/**
	 * Capture the version exposed by the given object.
	 */
	static Versioned of(VersionAware aware) {
		Assert.notNull(aware, "VersionAware must not be null");
		return of(aware.getVersion());
	}

	static Versioned of(ArtifactVersion artifactVersion) {
		Assert.notNull(artifactVersion, "ArtifactVersion must not be null");
		return new DefaultVersioned(artifactVersion);
	}

	static Versioned unversioned() {
		return Absent.INSTANCE;
	}

	boolean isVersioned();

	/**
	 * Invoke the action only when a version is present.
	 */
	default void ifPresent(Consumer<? super ArtifactVersion> action) {
		if (isVersioned()) {
			action.accept(getVersion());
		}
	}

	/**
	 * Return the version.
	 * @throws IllegalStateException if no version is present.
	 */
	@Override
	ArtifactVersion getVersion();

	/**
	 * Return the innermost artifact version.
	 * @throws IllegalStateException if no version is present.
	 */
	default ArtifactVersion unwrap() {
		return getVersion().unwrap();
	}

	/**
	 * Apply the mapper to the version as stored, without unwrapping it.
	 * @return an empty result if unversioned or the mapper returns {@literal null}.
	 */
	default <U> Optional<U> map(Function<? super ArtifactVersion, ? extends U> mapper) {
		Assert.notNull(mapper, "Mapper must not be null");
		return isVersioned() ? Optional.ofNullable(mapper.apply(getVersion())) : Optional.empty();
	}

	/**
	 * Return the version, invoking the supplier only if unversioned.
	 */
	default ArtifactVersion orElseGet(Supplier<ArtifactVersion> supplier) {
		Assert.notNull(supplier, "Supplier must not be null");
		return isVersioned() ? getVersion() : supplier.get();
	}

	enum Absent implements Versioned {

		INSTANCE;

		@Override
		public boolean isVersioned() {
			return false;
		}

		@Override
		public ArtifactVersion getVersion() {
			throw new IllegalStateException("No version present");
		}

		@Override
		public ArtifactVersion unwrap() {
			throw new IllegalStateException("No version present");
		}

		@Override
		public String toString() {
			return "unversioned";
		}

	}

	record DefaultVersioned(ArtifactVersion version) implements Versioned {

		public DefaultVersioned {
			Assert.notNull(version, "ArtifactVersion must not be null");
		}

		@Override
		public boolean isVersioned() {
			return true;
		}

		@Override
		public ArtifactVersion getVersion() {
			return this.version;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			DefaultVersioned that = (DefaultVersioned) o;
			return Objects.equals(version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(version);
		}

		@Override
		public String toString() {
			return version.toString();
		}

	}

}
