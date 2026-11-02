package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;
import java.util.List;

import static java.util.Objects.requireNonNull;

public sealed interface AwaitResult<OBSERVED, RESULT> {

    List<ConditionHistory<OBSERVED>> conditions();

    default RESULT orThrow() {
        return switch (this) {
            case Succeeded<OBSERVED, RESULT> success -> success.result();
            case Failed<OBSERVED, RESULT> failed -> {
                AwaitFailure failure = failed.failure();
                switch (failure.reason()) {
                    case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
                    default -> throw new AwaitExecutionException(failure);
                }
            }
        };
    }

    record Succeeded<OBSERVED, RESULT>(List<ConditionHistory<OBSERVED>> conditions,
            RESULT result) implements AwaitResult<OBSERVED, RESULT> {

        public Succeeded {
            conditions = List.copyOf(conditions);
        }

        @Override
        public String toString() {
            var text = new DiagnosticText(null);
            text.line("Wait satisfied");
            text.field("Result", text.value(result));
            text.histories(conditions);
            return text.finish("    ");
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
            return failure.message();
        }
    }
}
