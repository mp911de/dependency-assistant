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

package biz.paluch.dap.lookup;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;

import org.springframework.util.ObjectUtils;

/**
 * What a Dependency Site Find is centered on: the version backing a dependency.
 *
 * <p>A query names the {@link ArtifactId artifacts} whose declarations are of
 * interest, and the bare version-property names backing their version. The
 * version-property names drive discovery of definition and version-usage sites
 * (including sites that belong to other artifacts sharing the property); the
 * artifact ids drive discovery of inline definitions and accessor usages. A
 * query with no version properties is inline: there is no property to follow,
 * so the find reduces to the artifacts' own declaration sites.
 *
 * <p>Instances are immutable. Build them through {@link #create(Consumer)} with
 * a builder-configuring lambda.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
 */
public class DependencySiteQuery {

	private final Set<ArtifactId> artifacts;

	private final Set<String> versionProperties;

	private DependencySiteQuery(Set<ArtifactId> artifacts, Set<String> versionProperties) {
		this.artifacts = artifacts;
		this.versionProperties = versionProperties;
	}

	/**
	 * Create a query from the given builder consumer. The consumer populates a
	 * fresh {@link Builder}.
	 *
	 * @param builderConsumer configures the builder.
	 * @return the configured query.
	 */
	public static DependencySiteQuery create(Consumer<Builder> builderConsumer) {
		Builder builder = new Builder();
		builderConsumer.accept(builder);
		return builder.build();
	}

	/**
	 * Create a query centered on a single version property.
	 *
	 * @param propertyName the bare version-property name; must not be
	 * {@literal null}.
	 * @return a query with no artifacts and the given property.
	 */
	public static DependencySiteQuery ofProperty(String propertyName) {
		return create(it -> it.versionProperty(propertyName));
	}

	/**
	 * Create an inline query centered on a single artifact, with no version
	 * property.
	 *
	 * @param groupId the artifact group Id.
	 * @param artifactId the artifact Id.
	 * @return a query with the given artifact and no version property.
	 */
	public static DependencySiteQuery ofArtifact(String groupId, String artifactId) {
		return create(it -> it.artifact(ArtifactId.of(groupId, artifactId)));
	}

	/**
	 * Combine several queries into one, unioning their artifacts and version
	 * properties in encounter order.
	 *
	 * @param queries the queries to combine.
	 * @return a query covering every artifact and version property of the inputs;
	 * never {@literal null}.
	 */
	public static DependencySiteQuery union(Iterable<DependencySiteQuery> queries) {
		return create(builder -> {
			for (DependencySiteQuery query : queries) {
				builder.artifacts(query.artifacts).versionProperties(query.versionProperties);
			}
		});
	}

	/**
	 * Return the artifact coordinates of interest.
	 *
	 * @return the artifacts in encounter order; never {@literal null}, may be
	 * empty.
	 */
	public Set<ArtifactId> artifacts() {
		return artifacts;
	}

	/**
	 * Return the bare version-property names backing the version.
	 *
	 * @return the property names in encounter order; never {@literal null}, empty
	 * for an inline query.
	 */
	public Set<String> versionProperties() {
		return versionProperties;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof DependencySiteQuery that)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(artifacts, that.artifacts)
				&& ObjectUtils.nullSafeEquals(versionProperties, that.versionProperties);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(artifacts, versionProperties);
	}

	@Override
	public String toString() {
		return "DependencySiteQuery{artifacts=" + artifacts + ", versionProperties=" + versionProperties + '}';
	}

	/**
	 * Builder for {@link DependencySiteQuery} collecting artifacts and version
	 * properties in encounter order.
	 */
	public static class Builder {

		private final Set<ArtifactId> artifacts = new LinkedHashSet<>();

		private final Set<String> versionProperties = new LinkedHashSet<>();

		private Builder() {
		}

		/**
		 * Add an artifact of interest.
		 *
		 * @param artifact the artifact coordinates.
		 * @return {@code this} builder.
		 */
		public Builder artifact(ArtifactId artifact) {
			this.artifacts.add(artifact);
			return this;
		}

		/**
		 * Add several artifacts of interest.
		 *
		 * @param artifacts the artifact coordinates.
		 * @return {@code this} builder.
		 */
		public Builder artifacts(Iterable<ArtifactId> artifacts) {
			artifacts.forEach(this.artifacts::add);
			return this;
		}

		/**
		 * Add a bare version-property name backing the version.
		 *
		 * @param propertyName the property name.
		 * @return {@code this} builder.
		 */
		public Builder versionProperty(String propertyName) {
			this.versionProperties.add(propertyName);
			return this;
		}

		/**
		 * Add several bare version-property names backing the version.
		 *
		 * @param propertyNames the property names.
		 * @return {@code this} builder.
		 */
		public Builder versionProperties(Iterable<String> propertyNames) {
			propertyNames.forEach(this.versionProperties::add);
			return this;
		}

		/**
		 * Build a new immutable {@link DependencySiteQuery}.
		 *
		 * @return the configured query.
		 */
		public DependencySiteQuery build() {
			return new DependencySiteQuery(Collections.unmodifiableSet(new LinkedHashSet<>(artifacts)),
					Collections.unmodifiableSet(new LinkedHashSet<>(versionProperties)));
		}

	}

}
