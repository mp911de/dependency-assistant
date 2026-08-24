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

package biz.paluch.dap.lookup;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionSource;

import org.springframework.util.ObjectUtils;

/**
 * Immutable criteria for a Dependency Site Find.
 *
 * <p>Artifact coordinates identify declaration sites and artifact-addressable
 * usages, including inline definitions and version-catalog accessors. Bare
 * version-property names identify their definition and usage sites, including
 * sites for other artifacts sharing the property. A query may contain either or
 * both kinds of criteria.
 *
 * <p>Artifact and property sets preserve insertion order and are exposed as
 * unmodifiable snapshots. Build instances through {@link #create(Consumer)}.
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
	 * @param propertyName the bare version-property name.
	 * @return a query with no artifacts and the given property.
	 */
	public static DependencySiteQuery ofProperty(String propertyName) {
		return create(it -> it.versionProperty(propertyName));
	}

	/**
	 * Create a query centered on a single artifact, with no version-property
	 * criteria.
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
	 * @return a query covering every artifact and version property of the inputs.
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
	 * @return the unmodifiable artifacts in encounter order, possibly empty.
	 */
	public Set<ArtifactId> artifacts() {
		return artifacts;
	}

	/**
	 * Return the bare version-property names backing the version.
	 *
	 * @return the unmodifiable property names in encounter order, possibly empty.
	 */
	public Set<String> versionProperties() {
		return versionProperties;
	}

	/**
	 * Return whether the given property is a version property of interest.
	 *
	 * @param property the property to check.
	 * @return {@code true} if the query contains the property's bare name.
	 */
	public boolean matches(VersionSource.VersionProperty property) {
		return versionProperties().contains(property.getProperty());
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
		 * Build a new immutable {@link DependencySiteQuery} snapshot.
		 *
		 * @return the configured query.
		 */
		public DependencySiteQuery build() {
			return new DependencySiteQuery(Collections.unmodifiableSet(new LinkedHashSet<>(artifacts)),
					Collections.unmodifiableSet(new LinkedHashSet<>(versionProperties)));
		}

	}

}
