package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class AttemptHistory<OBSERVED, RESULT> implements Consumer<AttemptRecord<OBSERVED, RESULT>> {

    // ponytail: fixed cap; make it configurable only if consumers need longer diagnostic histories.
    private static final int MAX_RETAINED_ATTEMPTS = 256;

    private final ArrayList<AttemptRecord<OBSERVED, RESULT>> attempts = new ArrayList<>();

    @Override
    public void accept(AttemptRecord<OBSERVED, RESULT> attempt) {
        if (!attempts.isEmpty() && equivalent(attempts.getLast(), attempt)) {
            attempts.set(attempts.size() - 1, attempt);
            return;
        }
        if (attempts.size() == MAX_RETAINED_ATTEMPTS) {
            attempts.remove(1);
        }
        attempts.add(attempt);
    }

    List<AttemptRecord<OBSERVED, RESULT>> snapshot() {
        return List.copyOf(attempts);
    }

    private static boolean equivalent(AttemptRecord<?, ?> left, AttemptRecord<?, ?> right) {
        if (left.phase() != right.phase()
                || !(left.outcome() instanceof AttemptRecord.Outcome.Evaluated<?, ?> value)
                || !(right.outcome() instanceof AttemptRecord.Outcome.Evaluated<?, ?> other)
                || value.observed() != other.observed()
                || !equivalent(value.condition(), other.condition())
                || !equivalent(value.progress(), other.progress())) {
            return false;
        }
        return switch (value.evaluation()) {
            case ConditionResult.Satisfied<?> result
                    when other.evaluation() instanceof ConditionResult.Satisfied<?> next ->
                    result.result() == next.result();
            case ConditionResult.Unsatisfied<?> result
                    when other.evaluation() instanceof ConditionResult.Unsatisfied<?> next ->
                    result.mismatch().equals(next.mismatch()) && result.assertion() == next.assertion();
            default -> false;
        };
    }

    private static boolean equivalent(AttemptRecord.Progress left, AttemptRecord.Progress right) {
        return left == right || left != null && right != null
                && left.capturedStages() == right.capturedStages()
                && left.totalStages() == right.totalStages()
                && left.evaluatedStageNumber() == right.evaluatedStageNumber()
                && equivalent(left.condition(), right.condition());
    }

    private static boolean equivalent(Condition<?, ?> left, Condition<?, ?> right) {
        return left == right || left.description().equals(right.description())
                && Objects.equals(left.explanation(), right.explanation())
                && equivalent(left.reference(), right.reference());
    }

    private static boolean equivalent(Condition.Reference<?> left, Condition.Reference<?> right) {
        // Record equality would call user equals(); history only compares user values by identity.
        return left == right || left != null && right != null
                && left.label().equals(right.label()) && left.value() == right.value();
    }
}
