package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;
import java.util.List;

import static io.github.gromoff97.awium.internal.DiagnosticText.field;
import static java.util.Objects.requireNonNull;

/**
 * Public {@code tryAwait(...).until(...)} result containing the terminal value or failure and each condition's history.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <RESULT> final value returned by the wait
 */
public sealed interface AwaitResult<OBSERVED, RESULT> {

    /**
     * Returns an immutable list of condition histories in declaration order.
     * A single-condition wait has one history; captured waits have one per stage,
     * including empty histories for stages execution did not reach.
     */
    List<ConditionHistory<OBSERVED>> conditions();

    /**
     * Returns the successful value, including {@code null}, or throws the same kind of failure
     * as {@code await(...).until(...)}. Reading a result does not run the source or conditions again.
     *
     * @return the final value retained by a successful wait
     * @throws AwaitAssertionError if the wait timed out or its persistence check failed
     * @throws AwaitExecutionException if execution failed or the wait was interrupted
     */
    default RESULT orThrow() {
        return switch (this) {
            case Satisfied<OBSERVED, RESULT> success -> success.result();
            case Failed<OBSERVED, RESULT> failed -> {
                AwaitFailure failure = failed.failure();
                switch (failure.reason()) {
                    case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
                    default -> throw new AwaitExecutionException(failure);
                }
            }
        };
    }

    private static String withHistory(String summary, List<? extends ConditionHistory<?>> conditions) {
        var out = new StringBuilder(summary);
        for (int i = 0; i < conditions.size(); i++) {
            var history = conditions.get(i);
            out.append("\n\n").append(DiagnosticText.conditionHistory("Condition " + (i + 1), history));
        }
        return out.toString();
    }

    record Satisfied<OBSERVED, RESULT>(List<ConditionHistory<OBSERVED>> conditions,
            RESULT result) implements AwaitResult<OBSERVED, RESULT> {

        public Satisfied {
            conditions = List.copyOf(conditions);
        }

        @Override
        public String toString() {
            var summary = new StringBuilder("Wait satisfied\n");
            var text = new DiagnosticText(null);
            field(summary, "Result", text.value(result));
            text.appendFailures(summary, "    ");
            return withHistory(summary.substring(0, summary.length() - 1), conditions);
        }
    }

    record Failed<OBSERVED, RESULT>(List<ConditionHistory<OBSERVED>> conditions,
            AwaitFailure failure) implements AwaitResult<OBSERVED, RESULT> {

        public Failed {
            conditions = List.copyOf(conditions);
            requireNonNull(failure, "failure must not be null");
        }

        @Override
        public String toString() {
            return withHistory(failure.message(), conditions);
        }
    }
}
