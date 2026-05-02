/**
 * AssertJ assertions for Dependency Assistant tests.
 *
 * <p>This package provides domain-specific assertions around IntelliJ test
 * fixtures, PSI files, gutter marks, and collected dependency declarations.
 * Tests should normally use {@link biz.paluch.dap.assertions.Assertions} as the
 * static import entry point so project-specific assertions and standard AssertJ
 * assertions are available from the same {@code assertThat(...)} facade.
 *
 * <p>Assertion methods follow AssertJ's fluent style: methods either return the
 * same assertion object for further checks or navigate to a narrower assertion
 * object when the preceding check establishes the required precondition.
 *
 * <p>Example: <pre class="code">
 * import static biz.paluch.dap.assertions.Assertions.assertThat;
 *
 * assertThat(collector)
 *     .hasDependencyUsage("org.junit", "junit-bom")
 *     .hasVersion("6.0.3");
 *
 * assertThat(fixture)
 *     .hasSingleGutter()
 *     .tooltipContains("Patch", "6.0.3")
 *     .hasNavigation();
 * </pre>
 */
@org.jspecify.annotations.NullMarked
package biz.paluch.dap.assertions;
