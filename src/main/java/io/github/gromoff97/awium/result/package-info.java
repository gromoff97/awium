/**
 * Wait results, observed attempts, and failure diagnostics.
 * {@link io.github.gromoff97.awium.result.AwaitResult AwaitResult} distinguishes the
 * source observation type from the final value type and exposes one
 * {@link io.github.gromoff97.awium.result.ConditionHistory ConditionHistory} per condition occurrence.
 * Each history maps inclusive last attempt numbers to
 * {@link io.github.gromoff97.awium.result.Attempt Attempt} states, compacting consecutive
 * equal observations with repeated diagnostics.
 * Attempt variants distinguish completed checks from failures before or after observation.
 * History types retain only the observation type. Each attempt directly carries its diagnostics.
 * A result's {@code orThrow()} retrieves its successful value without repeating the wait.
 */
package io.github.gromoff97.awium.result;
