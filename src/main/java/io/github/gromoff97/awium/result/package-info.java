/**
 * Wait results, observed attempts, and failure diagnostics.
 * {@link io.github.gromoff97.awium.result.AwaitResult AwaitResult} distinguishes the
 * source observation type from the final value type;
 * {@link io.github.gromoff97.awium.result.AttemptGroup AttemptGroup} groups consecutive
 * diagnostic repetitions and keeps the first and last
 * {@link io.github.gromoff97.awium.result.HistoryEntry HistoryEntry}.
 * Both history types retain only the observation type. Attempt statuses carry no value type parameter.
 * A result's {@code orThrow()} retrieves its successful value without repeating the wait.
 */
package io.github.gromoff97.awium.result;
