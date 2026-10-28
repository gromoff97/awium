/**
 * Fluent waits and immutable condition definitions. Start with
 * {@link io.github.gromoff97.awium.Await#await(java.util.concurrent.Callable) await(source)},
 * configure the timing, and finish with {@code until(condition)} to retrieve a value
 * or {@code tryUntil(condition)} to retain the result and attempt history.
 * Sources and their returned objects remain caller-owned.
 */
package io.github.gromoff97.awium;
