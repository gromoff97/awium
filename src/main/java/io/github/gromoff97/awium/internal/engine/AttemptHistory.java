package io.github.gromoff97.awium.internal.engine;

import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class AttemptHistory<Observed, Result> implements Consumer<AwaitAttempt<Observed, Result>> {

    // ponytail: fixed cap; make it configurable only if consumers need longer diagnostic histories.
    private static final int MAX_RETAINED_ATTEMPTS = 256;

    private final ArrayList<AwaitAttempt<Observed, Result>> attempts = new ArrayList<>();

    @Override
    public void accept(AwaitAttempt<Observed, Result> attempt) {
        if (!attempts.isEmpty() && equivalent(attempts.getLast(), attempt)) {
            attempts.set(attempts.size() - 1, attempt);
            return;
        }
        if (attempts.size() == MAX_RETAINED_ATTEMPTS) {
            attempts.remove(1);
        }
        attempts.add(attempt);
    }

    List<AwaitAttempt<Observed, Result>> snapshot() {
        return List.copyOf(attempts);
    }

    private static boolean equivalent(AwaitAttempt<?, ?> left, AwaitAttempt<?, ?> right) {
        if (left.phase() != right.phase()) {
            return false;
        }
        return switch (left.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?> other ->
                    value.observed() == other.observed() && value.result() == other.result()
                            && equivalent(value.context(), other.context());
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.Unsatisfied<?, ?> other ->
                    value.observed() == other.observed()
                            && value.mismatch().equals(other.mismatch())
                            && value.assertion() == other.assertion()
                            && equivalent(value.context(), other.context());
            default -> false;
        };
    }

    private static boolean equivalent(AwaitAttempt.Context left, AwaitAttempt.Context right) {
        if (left == right) {
            return true;
        }
        if (left instanceof AwaitAttempt.Context.Expectation value
                && right instanceof AwaitAttempt.Context.Expectation other) {
            return value.description().equals(other.description()) && equivalent(value.reference(), other.reference());
        }
        return left instanceof AwaitAttempt.Context.Sequence value
                && right instanceof AwaitAttempt.Context.Sequence other
                && value.capturedStages() == other.capturedStages()
                && value.totalStages() == other.totalStages()
                && value.evaluatedStageNumber() == other.evaluatedStageNumber()
                && value.expectation().equals(other.expectation())
                && Objects.equals(value.importance(), other.importance())
                && equivalent(value.reference(), other.reference());
    }

    private static boolean equivalent(AwaitAttempt.Reference<?> left, AwaitAttempt.Reference<?> right) {
        // Record equality would call user equals(); history only compares user values by identity.
        return left == right || left != null && right != null
                && left.label().equals(right.label()) && left.value() == right.value();
    }
}
