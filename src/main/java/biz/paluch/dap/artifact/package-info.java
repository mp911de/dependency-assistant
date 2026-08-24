/**
 * Tool-agnostic dependency identities, versions, release histories, release
 * sources, and dependency-scan aggregates.
 *
 * <p>Build-file sites and update contracts live in
 * {@code biz.paluch.dap.support} and the build-tool integration packages.
 * Upgrade policy consumes this model without adding PSI or presentation
 * concerns to it.
 *
 * @see biz.paluch.dap.artifact.ArtifactId
 * @see biz.paluch.dap.artifact.ArtifactVersion
 * @see biz.paluch.dap.artifact.DependencyCollector
 * @see biz.paluch.dap.artifact.ReleaseSource
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap.artifact;
