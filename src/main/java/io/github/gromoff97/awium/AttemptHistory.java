package io.github.gromoff97.awium;

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
        if (left.phase() != right.phase()
                || !(left.outcome() instanceof AwaitAttempt.Outcome.Evaluated<?, ?> value)
                || !(right.outcome() instanceof AwaitAttempt.Outcome.Evaluated<?, ?> other)
                || value.observed() != other.observed()) {
            return false;
        }
        return switch (value.evaluation()) {
            case ConditionResult.Satisfied<?> result
                    when other.evaluation() instanceof ConditionResult.Satisfied<?> next ->
                    result.result() == next.result() && equivalent(result.context(), next.context());
            case ConditionResult.Unsatisfied<?> result
                    when other.evaluation() instanceof ConditionResult.Unsatisfied<?> next ->
                    result.mismatch().equals(next.mismatch()) && result.assertion() == next.assertion()
                            && equivalent(result.context(), next.context());
            default -> false;
        };
    }

    private static boolean equivalent(ConditionResult.Context left, ConditionResult.Context right) {
        if (left == right) {
            return true;
        }
        if (left instanceof ConditionResult.Context.Expectation value
                && right instanceof ConditionResult.Context.Expectation other) {
            return value.description().equals(other.description()) && equivalent(value.reference(), other.reference());
        }
        return left instanceof ConditionResult.Context.Sequence value
                && right instanceof ConditionResult.Context.Sequence other
                && value.capturedStages() == other.capturedStages()
                && value.totalStages() == other.totalStages()
                && value.evaluatedStageNumber() == other.evaluatedStageNumber()
                && value.expectation().equals(other.expectation())
                && Objects.equals(value.importance(), other.importance())
                && equivalent(value.reference(), other.reference());
    }

    private static boolean equivalent(ConditionResult.Reference<?> left, ConditionResult.Reference<?> right) {
        // Record equality would call user equals(); history only compares user values by identity.
        return left == right || left != null && right != null
                && left.label().equals(right.label()) && left.value() == right.value();
    }
}
