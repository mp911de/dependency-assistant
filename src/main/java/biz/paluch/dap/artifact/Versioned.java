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

package biz.paluch.dap.artifact;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A representation of a versioned artifact or project using
 * {@link ArtifactVersion} where the version can be {@link #unversioned()
 * undefined} or {@link #of(ArtifactVersion) present}.
 *
 * <p>Callers need to distinguish between a versioned and an unversioned state
 * through {@link #isVersioned()} before accessing {@link #getVersion()}, or
 * they use the higher-order helpers {@link #map(Function)} and
 * {@link #orElseGet(Supplier)} to avoid conditional branching at the call site.
 *
 * <p>{@link #unwrap()} follows the wrapper chain (see
 * {@link ArtifactVersion#isWrapped()}) and returns the innermost
 * {@link ArtifactVersion}.
 *
 * @author Mark Paluch
 * @see ArtifactVersion
 * @see VersionAware
 */
public interface Versioned extends VersionAware {

	/**
	 * Create a versioned container for the given artifact version.
	 *
	 * @param artifactVersion the version to wrap; must not be {@literal null}.
	 * @return a {@link Versioned} instance whose {@link #isVersioned()} returns
	 * {@literal true}.
	 */
	static Versioned of(ArtifactVersion artifactVersion) {
		Assert.notNull(artifactVersion, "ArtifactVersion must not be null");
		return new DefaultVersioned(artifactVersion);
	}

	/**
	 * Returns an empty (unversioned) {@link Versioned}.
	 */
	static Versioned unversioned() {
		return Absent.INSTANCE;
	}

	/**
	 * Return whether this container holds a version.
	 *
	 * @return {@literal true} if a version is present; {@literal false} for the
	 * unversioned instances.
	 */
	boolean isVersioned();

	/**
	 * Return the artifact version held by this container.
	 *
	 * <p>Callers must check {@link #isVersioned()} before invoking this method. The
	 * unversioned marker throws {@link IllegalStateException}.
	 *
	 * @return the artifact version; never {@literal null} when
	 * {@link #isVersioned()} is {@literal true}.
	 * @throws IllegalStateException if no version is present.
	 */
	@Override
	ArtifactVersion getVersion();

	/**
	 * Traverse the wrapper chain and return the innermost {@link ArtifactVersion}.
	 *
	 * <p>Delegates to {@link ArtifactVersion#getVersion()} until
	 * {@link ArtifactVersion#isWrapped()} returns {@literal false}.
	 *
	 * @return the unwrapped artifact version; never {@literal null}.
	 * @throws IllegalStateException if no version is present.
	 */
	default ArtifactVersion unwrap() {
		ArtifactVersion version = getVersion();
		while (version.isWrapped()) {
			version = version.getVersion();
		}
		return version;
	}

	/**
	 * Apply the given mapping function to the version if present, and return the
	 * result.
	 *
	 * <p>The mapper receives the version as returned by {@link #getVersion()},
	 * <em>not</em> the unwrapped form. Call {@link #unwrap()} first if you need the
	 * innermost version.
	 *
	 * @param mapper the mapping function to apply to a value, if present
	 * @param <U>    The type of the value returned from the mapping function
	 * @return an {@link Optional} describing the result of applying a mapping
	 * function to the value of this {@code Versioned}, if a value is present,
	 * otherwise an empty {@code link}.
	 */
	default <U> @Nullable Optional<U> map(Function<? super ArtifactVersion, ? extends U> mapper) {
		Assert.notNull(mapper, "Mapper must not be null");
		return isVersioned() ? Optional.ofNullable(mapper.apply(getVersion())) : Optional.empty();
	}

	/**
	 * Return the version if present; otherwise invoke the given supplier and return
	 * its result.
	 *
	 * @param supplier the fallback supplier invoked when no version is present;
	 *                 must not be {@literal null}.
	 * @return the version held by this container, or the value produced by the
	 * supplier; never {@literal null} when the supplier itself is non-null.
	 */
	default ArtifactVersion orElseGet(Supplier<ArtifactVersion> supplier) {
		Assert.notNull(supplier, "Supplier must not be null");
		return isVersioned() ? getVersion() : supplier.get();
	}

	/**
	 * Absent (unversioned) implemented as an enum singleton.
	 */
	enum Absent implements Versioned {

		/**
		 * The single absent instance.
		 */
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

	/**
	 * Simple versioned container backed by {@link ArtifactVersion}.
	 */
	record DefaultVersioned(ArtifactVersion version) implements Versioned {

		/**
		 * Create a versioned container.
		 *
		 * @param version the artifact version to wrap; must not be {@literal null}.
		 */
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
