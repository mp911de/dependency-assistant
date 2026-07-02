/**
 * Core support for the Dependency Assistant plugin.
 *
 * <p>Provides the shared project state population substrate that each
 * build-tool integration plugs into:
 * <ul>
 * <li>{@link biz.paluch.dap.ProjectStateIndexer} as cross-ecosystem coordinator
 * that owns the collect-complete-store flow for one run.</li>
 * <li>{@link biz.paluch.dap.DependencyAssistant} SPI implemented by each
 * integration to enumerate anchor files, collect their dependencies, and
 * produce file-scoped contexts.</li>
 * <li>{@link biz.paluch.dap.IntrospectedDependencies}s phase-scoped completion
 * handle for one indexer run, applied to phase-one collectors before the
 * Project State is stored.</li>
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap;
