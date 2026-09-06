package io.github.gromoff97.awium.internal.condition;

import io.github.gromoff97.awium.condition.AwaitCondition;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.CheckedFunction;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.condition.Condition.ExpectedCondition;
import io.github.gromoff97.awium.condition.Condition.NarrowingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.results.AwaitAttempt.Reference;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

/**
 * Construction and execution support shared by the await facade and condition catalogs.
 */
public final class ConditionRuntime {

    private interface RuntimeStage<Observed, Result> {

        ConditionMetadata metadata();

        Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory();

        default Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> newEvaluator() {
            return new Function<Observed, ConditionEvaluation<? extends Result>>() {

                private CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluator;

                @Override
                public ConditionEvaluation<? extends Result> apply(Observed actual) {
                    try {
                        if (evaluator == null) {
                            evaluator = requireNonNull(evaluatorFactory().call(), "evaluator must not be null");
                        }
                        return evaluator.apply(actual);
                    } catch (RuntimeException failure) {
                        throw failure;
                    } catch (Exception failure) {
                        return uncontrolled(failure);
                    }
                }
            };
        }
    }

    public record RuntimeCondition<Observed, Result>(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory)
            implements Condition<Observed, Result>, RuntimeStage<Observed, Result> {
    }

    public record RuntimePreservingCondition<Observed>(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Observed>>> evaluatorFactory)
            implements PreservingCondition<Observed>, RuntimeStage<Observed, Observed> {
    }

    public record RuntimeExpectedCondition<Expected>(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Object, ? extends ConditionEvaluation<? extends Object>>> evaluatorFactory)
            implements ExpectedCondition<Expected>, RuntimeStage<Object, Object> {
    }

    public record RuntimeNarrowingCondition<Result>(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Object, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory)
            implements NarrowingCondition<Result>, RuntimeStage<Object, Result> {
    }

    public record RuntimeSelectedCondition<Observed, Family extends Source<?>>(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<?>>> evaluatorFactory)
            implements SelectedCondition<Observed, Family>, RuntimeStage<Observed, Object> {
    }

    public static <Observed, Result> Condition<Observed, Result> condition(String description,
            CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluation) {
        return condition(description, null, evaluation);
    }

    public static <Observed, Result> Condition<Observed, Result> condition(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluation) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluation, "evaluation must not be null");
        return new RuntimeCondition<>(metadata, () -> evaluation);
    }

    public static <Observed, Result> Condition<Observed, Result> conditionFactory(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory) {
        return conditionFactory(new ConditionMetadata(description, null, null), evaluatorFactory);
    }

    public static <Observed, Result> Condition<Observed, Result> conditionFactory(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory) {
        return new RuntimeCondition<>(requireNonNull(metadata, "metadata must not be null"),
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Observed> Condition<Observed, List<Observed>> captured(Predicate<? super Observed> first,
            Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return capturedPreserving(stages("predicate", first, second, rest).stream()
                .map(predicate -> ConditionRuntime.<Observed>preserving("value matches", actual -> predicate.test(actual)
                        ? satisfied(actual) : unsatisfied("value did not match"))).toList());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Observed> Condition<Observed, List<Observed>> captured(PreservingCondition<? super Observed> first,
            PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return capturedPreserving(stages("condition", first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Observed, Result> Condition<Observed, List<Result>> captured(Condition<? super Observed, ? extends Result> first,
            Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        List<Condition<? super Observed, ? extends Result>> stages = stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<Observed, Result>evaluator(stage))).toList())::apply);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Observed, Expected extends Observed> Condition<Observed, List<Observed>> captured(ExpectedCondition<? extends Expected> first,
            ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        List<ExpectedCondition<? extends Expected>> stages = stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<Observed>preservingEvaluator(stage))).toList())::apply);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Observed, Element, Family extends Source<?>> Condition<Observed, List<Element>> captured(SelectedCondition<? super Observed, Family> first,
            SelectedCondition<? super Observed, Family> second,
            SelectedCondition<? super Observed, Family>... rest) {
        List<SelectedCondition<? super Observed, Family>> stages =
                stages("condition", first, second, rest);
        return conditionFactory("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<Observed, Element>evaluator(stage))).toList())::apply);
    }

    private static <Observed> Condition<Observed, List<Observed>> capturedPreserving(List<? extends PreservingCondition<? super Observed>> stages) {
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage,
                                ConditionRuntime.<Observed>preservingEvaluator(stage))).toList())::apply);
    }

    private static <Observed, Result> CapturedEvaluator.Stage<Observed, Result> capturedStage(AwaitCondition stage,
            Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluator) {
        return new CapturedEvaluator.Stage<>(evaluator, metadata(stage));
    }

    private static <Stage> List<Stage> stages(String name, Stage first, Stage second, Stage[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, name + "s must not be null")))
                .map(stage -> requireNonNull(stage, name + " must not be null"))
                .toList();
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description,
            CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Observed>> evaluator) {
        return preserving(description, null, evaluator);
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Observed>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimePreservingCondition<>(metadata, () -> evaluator);
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionEvaluation<? extends Observed>>> evaluatorFactory) {
        return new RuntimePreservingCondition<>(new ConditionMetadata(description, null, null),
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    public static <Expected> ExpectedCondition<Expected> expected(String description, Reference<?> reference,
            Function<Object, ConditionEvaluation<Object>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeExpectedCondition<>(metadata, () -> evaluator::apply);
    }

    public static <Result> NarrowingCondition<Result> narrowing(String description, Function<Object, ConditionEvaluation<Result>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeNarrowingCondition<>(metadata, () -> evaluator::apply);
    }

    public static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> selected(String description,
            Function<? super Observed, ? extends ConditionEvaluation<?>> evaluator) {
        return selected(description, null, evaluator);
    }

    public static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> selected(String description,
            Reference<?> reference, Function<? super Observed, ? extends ConditionEvaluation<?>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeSelectedCondition<>(metadata, () -> evaluator::apply);
    }

    @SuppressWarnings("unchecked")
    public static <Observed, Result> Function<Observed, ConditionEvaluation<Result>> evaluator(AwaitCondition condition) {
        return (Function<Observed, ConditionEvaluation<Result>>) (Function<?, ?>)
                ConditionRuntime.<Observed, Result>runtime(condition).newEvaluator();
    }

    public static <Observed> Function<Observed, ConditionEvaluation<Observed>> preservingEvaluator(AwaitCondition condition) {
        Function<? super Observed, ? extends ConditionEvaluation<?>> evaluator = ConditionRuntime.<Observed, Observed>runtime(condition).newEvaluator();
        return actual -> {
            ConditionEvaluation<?> evaluation = evaluator.apply(actual);
            return evaluation == null ? null : evaluation.mapSatisfied(ignored -> actual);
        };
    }

    public static <Observed, Result> Condition<Observed, Result> explained(Condition<Observed, Result> condition,
            String explanation) {
        RuntimeStage<Observed, Result> runtime = runtime(condition);
        return new RuntimeCondition<>(runtime.metadata().because(explanation), runtime.evaluatorFactory());
    }

    public static <Observed> PreservingCondition<Observed> explained(PreservingCondition<Observed> condition, String explanation) {
        RuntimeStage<Observed, Observed> runtime = runtime(condition);
        return new RuntimePreservingCondition<>(runtime.metadata().because(explanation), runtime.evaluatorFactory());
    }

    public static <Expected> ExpectedCondition<Expected> explained(ExpectedCondition<Expected> condition, String explanation) {
        RuntimeStage<Object, Object> runtime = runtime(condition);
        return new RuntimeExpectedCondition<>(runtime.metadata().because(explanation), runtime.evaluatorFactory());
    }

    public static <Result> NarrowingCondition<Result> explained(NarrowingCondition<Result> condition, String explanation) {
        RuntimeStage<Object, Result> runtime = runtime(condition);
        return new RuntimeNarrowingCondition<>(runtime.metadata().because(explanation), runtime.evaluatorFactory());
    }

    public static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> explained(SelectedCondition<Observed, Family> condition,
            String explanation) {
        RuntimeStage<Observed, Object> runtime = runtime(condition);
        return new RuntimeSelectedCondition<>(runtime.metadata().because(explanation), runtime.evaluatorFactory());
    }

    public static ConditionMetadata metadata(AwaitCondition condition) {
        return runtime(condition).metadata();
    }

    public static <Value> Reference<Value> expectedReference(Value value) {
        return new Reference<>("Expected", value);
    }

    public static <Value> Reference<Value> unexpectedReference(Value value) {
        return new Reference<>("Unexpected", value);
    }

    @SuppressWarnings("unchecked")
    private static <Observed, Result> RuntimeStage<Observed, Result> runtime(AwaitCondition condition) {
        return (RuntimeStage<Observed, Result>) requireNonNull(condition, "condition must not be null");
    }

    private ConditionRuntime() {
        throw new AssertionError("Utility class");
    }
}
