package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.AwaitCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.Conditions;
import io.github.gromoff97.awium.sources.Source;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionRuntime {

    private interface RuntimeStage<S, R> {

        String description();

        String explanation();

        Supplier<? extends Function<? super S, ? extends Evaluation<? extends R>>> evaluatorFactory();

        default Function<? super S, ? extends Evaluation<? extends R>> newEvaluator() {
            return requireNonNull(evaluatorFactory().get(), "evaluator must not be null");
        }
    }

    public record RuntimeCondition<S, R>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends R>>> evaluatorFactory)
            implements Condition<S, R>, RuntimeStage<S, R> {
    }

    public record RuntimePreservingCondition<S>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends S>>> evaluatorFactory)
            implements PreservingCondition<S>, RuntimeStage<S, S> {
    }

    public record RuntimeExpectedCondition<T>(String description, String explanation,
            Supplier<? extends Function<? super Object, ? extends Evaluation<? extends Object>>> evaluatorFactory)
            implements ExpectedCondition<T>, RuntimeStage<Object, Object> {
    }

    public record RuntimeSelectedCondition<S, F extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<?>>> evaluatorFactory)
            implements SelectedCondition<S, F>, RuntimeStage<S, Object> {
    }

    public record RuntimeSelectedSequenceCondition<S, F extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends List<Object>>>> evaluatorFactory)
            implements SelectedSequenceCondition<S, F>, RuntimeStage<S, List<Object>> {
    }

    public static <S, R> Condition<S, R> condition(String description,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends R>>> evaluatorFactory) {
        return new RuntimeCondition<>(nonBlank(description, "description"), null,
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    public static <S, R> Condition<S, R> condition(String description,
            Function<? super S, Evaluation<R>> evaluation) {
        String validatedDescription = nonBlank(description, "description");
        requireNonNull(evaluation, "evaluation must not be null");
        return new RuntimeCondition<>(validatedDescription, null, () -> evaluation);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S> Condition<S, List<S>> captured(Predicate<? super S> first,
            Predicate<? super S> second, Predicate<? super S>... rest) {
        return capturedPreserving(stages("predicate", first, second, rest).stream()
                .map(predicate -> Conditions.<S>matches(predicate)).toList());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S> Condition<S, List<S>> captured(PreservingStage<? super S> first,
            PreservingStage<? super S> second, PreservingStage<? super S>... rest) {
        return capturedPreserving(stages("condition", first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, R> Condition<S, List<R>> captured(ResultStage<S, R> first,
            ResultStage<S, R> second, ResultStage<S, R>... rest) {
        List<ResultStage<S, R>> stages = stages("condition", first, second, rest);
        return condition("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<S, R>evaluator(stage))).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, F extends Source<?>> SelectedSequenceCondition<S, F> captured(SelectedStage<? super S, F> first,
            SelectedStage<? super S, F> second,
            SelectedStage<? super S, F>... rest) {
        List<SelectedStage<? super S, F>> stages =
                stages("condition", first, second, rest);
        return new RuntimeSelectedSequenceCondition<>("conditions are satisfied in order", null,
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, selectedEvaluator(stage))).toList()));
    }

    private static <S> Condition<S, List<S>> capturedPreserving(List<? extends PreservingStage<? super S>> stages) {
        return condition("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage,
                                ConditionRuntime.<S>preservingEvaluator(stage))).toList()));
    }

    private static <S, R> CapturedEvaluator.Stage<S, R> capturedStage(AwaitCondition stage,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator) {
        return new CapturedEvaluator.Stage<>(evaluator, description(stage), explanation(stage));
    }

    private static <T> List<T> stages(String name, T first, T second, T[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, name + "s must not be null")))
                .map(stage -> requireNonNull(stage, name + " must not be null"))
                .toList();
    }

    public static <S> PreservingCondition<S> preserving(String description,
            Function<S, Evaluation<S>> evaluator) {
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimePreservingCondition<>(nonBlank(description, "description"), null, () -> evaluator);
    }

    public static <T> ExpectedCondition<T> expected(String description, Function<Object, Evaluation<Object>> evaluator) {
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeExpectedCondition<>(nonBlank(description, "description"), null, () -> evaluator);
    }

    public static <S, F extends Source<?>> SelectedCondition<S, F> selected(String description,
            Function<? super S, ? extends Evaluation<?>> evaluator) {
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeSelectedCondition<>(nonBlank(description, "description"), null, () -> evaluator);
    }

    @SuppressWarnings("unchecked")
    public static <S, R> Function<S, Evaluation<R>> selectedEvaluator(AwaitCondition condition) {
        return (Function<S, Evaluation<R>>) (Function<?, ?>) ConditionRuntime.<S, R>runtime(condition).newEvaluator();
    }

    public static <S, R> Function<? super S, ? extends Evaluation<? extends R>> evaluator(
            ConditionStage<? super S, ? extends R> condition) {
        return ConditionRuntime.<S, R>runtime(condition).newEvaluator();
    }

    public static <S> Function<S, Evaluation<S>> preservingEvaluator(PreservingStage<? super S> condition) {
        Function<? super S, ? extends Evaluation<?>> evaluator = ConditionRuntime.<S, S>runtime(condition).newEvaluator();
        return actual -> {
            Evaluation<?> evaluation = evaluator.apply(actual);
            return evaluation == null ? null : evaluation.continueIfSatisfied(ignored -> satisfied(actual));
        };
    }

    public static <S> Function<S, Evaluation<S>> expectedEvaluator(ExpectedStage<?> condition) {
        Function<? super Object, ? extends Evaluation<?>> evaluator = ConditionRuntime.<Object, Object>runtime(condition).newEvaluator();
        return actual -> {
            Evaluation<?> evaluation = evaluator.apply(actual);
            return evaluation == null ? null : evaluation.continueIfSatisfied(ignored -> satisfied(actual));
        };
    }

    public static <S, R> ResultStage<S, R> explained(Condition<S, R> condition,
            String explanation) {
        RuntimeStage<S, R> runtime = runtime(condition);
        return new RuntimeCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    public static <S> PreservingStage<S> explained(PreservingCondition<S> condition, String explanation) {
        RuntimeStage<S, S> runtime = runtime(condition);
        return new RuntimePreservingCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    public static <T> ExpectedStage<T> explained(ExpectedCondition<T> condition, String explanation) {
        RuntimeStage<Object, Object> runtime = runtime(condition);
        return new RuntimeExpectedCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    public static <S, F extends Source<?>> SelectedStage<S, F> explained(SelectedCondition<S, F> condition, String explanation) {
        RuntimeStage<S, Object> runtime = runtime(condition);
        return new RuntimeSelectedCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    public static <S, F extends Source<?>> SelectedSequenceStage<S, F> explained(SelectedSequenceCondition<S, F> condition, String explanation) {
        RuntimeStage<S, List<Object>> runtime = runtime(condition);
        return new RuntimeSelectedSequenceCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    public static String description(AwaitCondition condition) {
        return runtime(condition).description();
    }

    public static String explanation(AwaitCondition condition) {
        return runtime(condition).explanation();
    }

    @SuppressWarnings("unchecked")
    private static <S, R> RuntimeStage<S, R> runtime(AwaitCondition condition) {
        return (RuntimeStage<S, R>) requireNonNull(condition, "condition must not be null");
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private ConditionRuntime() {
        throw new AssertionError("Utility class");
    }
}
