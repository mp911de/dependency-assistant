/**
 * Resolves captured upstream project metadata into repository, issue-tracker,
 * release-note, and repository-tag information.
 *
 * <p>Hosting {@link biz.paluch.dap.metadata.Platform} extensions recognize
 * normalized repository URLs and create platform-specific repository handles.
 * Metadata indexing and lookup connect those handles to the persistent project
 * cache.
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap.metadata;
