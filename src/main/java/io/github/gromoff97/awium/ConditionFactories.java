package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionResult.Reference;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

/**
 * Condition construction shared by the fluent facade and condition catalogs.
 */
final class ConditionFactories {

    static <Observed, Result> Condition<Observed, Result> condition(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluation) {
        return condition(description, null, evaluation);
    }

    static <Observed, Result> Condition<Observed, Result> condition(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluation) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>(metadata, () -> evaluation);
    }

    static <Observed, Result> Condition<Observed, Result> conditionFactory(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return conditionFactory(new ConditionMetadata(description, null, null), evaluatorFactory);
    }

    static <Observed, Result> Condition<Observed, Result> conditionFactory(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return new Condition<>(requireNonNull(metadata, "metadata must not be null"),
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed> Condition<Observed, List<Observed>> captured(Predicate<? super Observed> first,
            Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return capturedPreserving(stages("predicate", first, second, rest).stream()
                .map(predicate -> ConditionFactories.<Observed>preserving("value matches", actual -> predicate.test(actual)
                        ? satisfied(actual) : unsatisfied("value did not match"))).toList());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed> Condition<Observed, List<Observed>> captured(PreservingCondition<? super Observed> first,
            PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return capturedPreserving(stages("condition", first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Result> Condition<Observed, List<Result>> captured(Condition<? super Observed, ? extends Result> first,
            Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        List<Condition<? super Observed, ? extends Result>> stages = stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<Observed, Result>(stages.stream()
                        .map(stage -> stage.newSession()).toList())::apply);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Expected extends Observed> Condition<Observed, List<Observed>> captured(ExpectedCondition<? extends Expected> first,
            ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        List<ExpectedCondition<? extends Expected>> stages = stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<Observed>preservingSession()).toList())::apply);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Element, Family extends Source<?>> Condition<Observed, List<Element>> captured(SelectedCondition<? super Observed, Family> first,
            SelectedCondition<? super Observed, Family> second,
            SelectedCondition<? super Observed, Family>... rest) {
        List<SelectedCondition<? super Observed, Family>> stages =
                stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<Element>selectionSession()).toList())::apply);
    }

    private static <Observed> Condition<Observed, List<Observed>> capturedPreserving(List<? extends PreservingCondition<? super Observed>> stages) {
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<Observed>preservingSession()).toList())::apply);
    }

    private static <Stage> List<Stage> stages(String name, Stage first, Stage second, Stage[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, name + "s must not be null")))
                .map(stage -> requireNonNull(stage, name + " must not be null"))
                .toList();
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

    static <Observed> PreservingCondition<Observed> preserving(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>>> evaluatorFactory) {
        return new PreservingCondition<>(new ConditionMetadata(description, null, null),
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    static <Expected> ExpectedCondition<Expected> expected(String description, Reference<?> reference,
            Function<Object, ConditionResult<Object>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new ExpectedCondition<>(metadata, () -> evaluator::apply);
    }

    static <Result> NarrowingCondition<Result> narrowing(String description, Function<Object, ConditionResult<Result>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new NarrowingCondition<>(metadata, () -> evaluator::apply);
    }

    static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> selected(String description,
            Function<? super Observed, ? extends ConditionResult<?>> evaluator) {
        return selected(description, null, evaluator);
    }

    static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> selected(String description,
            Reference<?> reference, Function<? super Observed, ? extends ConditionResult<?>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new SelectedCondition<>(metadata, () -> evaluator::apply);
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
