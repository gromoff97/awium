package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionResult.Reference;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/**
 * Creates immutable condition definitions for catalogs and composition.
 */
final class ConditionFactories {

    static <Observed, Result> Condition<Observed, Result> condition(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluation) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>(metadata, () -> evaluation);
    }

    static <Observed, Result> Condition<Observed, Result> conditionFactory(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return new Condition<>(new ConditionMetadata(description, null, null), evaluatorFactory);
    }

    static <Observed> PreservingCondition<Observed> preserving(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <Observed> PreservingCondition<Observed> preserving(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(metadata, () -> evaluator);
    }

    static <Expected> ExpectedCondition<Expected> expected(String description, Reference<?> reference,
            CheckedFunction<Object, ConditionResult<Object>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new ExpectedCondition<>(metadata, () -> evaluator);
    }

    static <Result> NarrowingCondition<Result> narrowing(String description, CheckedFunction<Object, ConditionResult<Result>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new NarrowingCondition<>(metadata, () -> evaluator);
    }

    static <Observed, Family> SelectedCondition<Observed, Family> selected(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<?>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new SelectedCondition<>(metadata, () -> evaluator);
    }

    static <Value> Reference<Value> expectedReference(Value value) {
        return new Reference<>("Expected", value);
    }

    static <Value> Reference<Value> unexpectedReference(Value value) {
        return new Reference<>("Unexpected", value);
    }

    private ConditionFactories() {
        throw new AssertionError("Utility class");
    }
}
