/**
 * Fluent waits and immutable condition definitions. Start with
 * {@link io.github.gromoff97.awium.Await#await(java.util.concurrent.Callable) await(source)},
 * or {@link io.github.gromoff97.awium.Await#tryAwait(java.util.concurrent.Callable) tryAwait(source)},
 * configure the timing, and finish with {@code until(condition)}. The first entry point
 * retrieves a value; the second retains a result with attempt history.
 * Sources and their returned objects remain caller-owned.
 */
package io.github.gromoff97.awium;
