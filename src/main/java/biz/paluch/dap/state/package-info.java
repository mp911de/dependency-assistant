/**
 * Persistent and runtime state abstractions for the Dependency Assistant
 * plugin.
 * <p>
 * The package separates two related concerns:
 * <ul>
 * <li>persisted cache data such as known releases and property-to-artifact
 * correlations, and</li>
 * <li>runtime project views that expose the currently analyzed dependencies of
 * a Gradle project.</li>
 * </ul>
 * The persistent model is intentionally simple and XML-serializer friendly. It
 * should be treated primarily as a storage representation whose public API
 * defines lookup and update semantics for higher-level services.
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap.state;
