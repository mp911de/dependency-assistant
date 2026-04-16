package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A Gradle dependency.
 *
 * @author Mark Paluch
 */
interface GradleDependency {

	/**
	 * @return the artifact identifier.
	 */
	ArtifactId getId();

	/**
	 * @return the version source.
	 */
	VersionSource getVersionSource();

	/**
	 * Parse a Gradle GAV string {@code groupId:artifactId:version} or
	 * {@code groupId:artifactId} into a {@code GradleDependency}.
	 *
	 * @param gav the GAV string.
	 * @return the parsed {@code GradleDependency} or {@literal null} if the string
	 * could not be parsed because of e.g. missing segments.
	 */
	static @Nullable GradleDependency parse(String gav) {
		return parse(gav, new PropertyResolver() {

			@Override
			public boolean containsProperty(String key) {
				return false;
			}

			@Override
			public @Nullable String getProperty(String key) {
				return null;
			}

		});
	}

	/**
	 * Parse a Gradle GAV string {@code groupId:artifactId:version} or
	 * {@code groupId:artifactId} into a {@code GradleDependency}. Parsing resolves
	 * {@code groupId} and {@code artifactId} and returns the {@link VersionSource}
	 * accordingly if the version is a property.
	 *
	 * @param gav the GAV string.
	 * @param propertyResolver the property resolver to resolve properties.
	 * @return the parsed {@code GradleDependency} or {@literal null} if the string
	 * could not be parsed because of e.g. missing segments.
	 */
	static @Nullable GradleDependency parse(String gav, PropertyResolver propertyResolver) {

		String[] parts = gav.split(":");
		return parts.length < 2 ? null : of(parts[0], parts[1], parts.length > 2 ? parts[2] : null, propertyResolver);
	}

	/**
	 * Parse a Gradle IDV string {@code pluginId} or {@code pluginId:version} into a
	 * {@code GradleDependency}.
	 *
	 * @param idv the GAV string.
	 * @return the parsed {@code GradleDependency} or {@literal null} if the string
	 * could not be parsed because of e.g. missing segments.
	 */
	static @Nullable GradleDependency parsePlugin(String idv) {
		return parsePlugin(idv, new PropertyResolver() {

			@Override
			public boolean containsProperty(String key) {
				return false;
			}

			@Override
			public @Nullable String getProperty(String key) {
				return null;
			}

		});
	}

	/**
	 * Parse a Gradle IDV string {@code pluginId} or {@code pluginId:version} into a
	 * {@code GradleDependency}. Parsing resolves {@code groupId} and
	 * {@code artifactId} and returns the {@link VersionSource} accordingly if the
	 * version is a property.
	 *
	 * @param gav the GAV string.
	 * @param propertyResolver the property resolver to resolve properties.
	 * @return the parsed {@code GradleDependency} or {@literal null} if the string
	 * could not be parsed because of e.g. missing segments.
	 */
	static @Nullable GradleDependency parsePlugin(String gav, PropertyResolver propertyResolver) {

		String[] parts = gav.split(":");
		return parts.length < 1 ? null : of(parts[0], parts[0], parts.length > 1 ? parts[1] : null, propertyResolver);
	}

	/**
	 * Create a new {@code GradleDependency} from group, artifact, and version
	 * strings.
	 * @param g groupId.
	 * @param a artifactId.
	 * @param v version, which may be {@code null} or a property reference like
	 * {@code ${version}}.
	 * @return the created {@code GradleDependency}.
	 */
	static GradleDependency of(String g, String a, @Nullable String v) {
		return of(g, a, v, key -> null);
	}

	/**
	 * Create a new {@code GradleDependency} from group, artifact, and version
	 * strings.
	 * @param g groupId.
	 * @param a artifactId.
	 * @param v version, which may be {@code null} or a property reference like
	 * {@code ${version}}.
	 * @return the created {@code GradleDependency}.
	 */
	static GradleDependency of(String g, String a, @Nullable String v, PropertyResolver resolver) {

		ArtifactId artifactId = getArtifactId(g, a, resolver);
		if (StringUtils.hasText(v)) {

			PropertyExpression expression = PropertyExpression.from(v);
			if (expression.isProperty()) {
				return new PropertyManagedDependency(artifactId, expression.getPropertyName(),
						VersionSource.property(expression.getPropertyName()));
			}

			return new SimpleDependency(artifactId, v, VersionSource.declared(v));
		}

		return new DependencyReference(artifactId);
	}

	/**
	 * Resolve the artifact id from the given group and artifact.
	 */
	public static ArtifactId getArtifactId(String g, String a, PropertyResolver resolver) {

		PropertyExpression group = PropertyExpression.from(g);
		PropertyExpression artifact = PropertyExpression.from(a);

		return ArtifactId.of(group.resolveRequired(resolver),
				artifact.resolveRequired(resolver));
	}

	/**
	 * Create a new {@code GradleDependency} using {@link ArtifactId} and a version
	 * to determine whether it is a version-managed artifact.
	 * @param artifactId artifact identifier.
	 * @param versionExpression the property expression defining the version.
	 * @return a new {@code GradleDependency}.
	 */
	static GradleDependency of(ArtifactId artifactId, PropertyExpression versionExpression) {

		if (versionExpression.isProperty()) {
			return new PropertyManagedDependency(artifactId, versionExpression.getPropertyName(),
					VersionSource.property(versionExpression.getPropertyName()));
		}
		return new SimpleDependency(artifactId, versionExpression.toString(),
				VersionSource.declared(versionExpression.toString()));
	}

	/**
	 * Create a new {@code GradleDependency} with the same artifact but a different
	 * version source.
	 * @param versionExpression
	 * @return
	 */
	default GradleDependency withVersion(PropertyExpression versionExpression) {
		return of(getId(), versionExpression);
	}

	record DependencyReference(ArtifactId id) implements GradleDependency {

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return VersionSource.none();
		}

	}

	record SimpleDependency(ArtifactId id, String version, VersionSource versionSource) implements GradleDependency {

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}

	}

	record PropertyManagedDependency(ArtifactId id, String property,
			VersionSource versionSource) implements GradleDependency {

		public static PropertyManagedDependency of(ArtifactId artifactId, PropertyExpression versionExpression) {
			return new PropertyManagedDependency(artifactId, versionExpression.getPropertyName(),
					VersionSource.property(versionExpression.getPropertyName()));
		}

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}

	}

}
