/**
 * Core support for the Dependency Assistant plugin.
 *
 * <p>
 * Houses the shared Project State population substrate that each build-tool
 * integration plugs into:
 * <ul>
 * <li>{@link biz.paluch.dap.ProjectStateUpdater} — cross-ecosystem coordinator
 * that owns the collect-complete-store flow for one run.</li>
 * <li>{@link biz.paluch.dap.DependencySource} — SPI implemented by each
 * integration to enumerate, collect, and create file-scoped entries.</li>
 * <li>{@link biz.paluch.dap.DependencyScanEntry} — carrier passed between the
 * phases of one run, pairing an anchor file with its build context.</li>
 * <li>{@link biz.paluch.dap.IntrospectedDependencies} — phase-scoped completion
 * handle for one updater run, applied to phase-one collectors before the
 * Project State is stored.</li>
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap;
