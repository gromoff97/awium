package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.ObjectCondition;
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

    public record RuntimeCondition<S, R>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends R>>> evaluatorFactory) implements Condition<S, R> {
    }

    public record RuntimePreservingCondition<S>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends S>>> evaluatorFactory) implements PreservingCondition<S> {
    }

    public record RuntimeSelectedCondition<S, F extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<?>>> evaluatorFactory) implements SelectedCondition<S, F> {
    }

    public record RuntimeSelectedSequenceCondition<S, F extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super S, ? extends Evaluation<? extends List<Object>>>> evaluatorFactory) implements SelectedSequenceCondition<S, F> {
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
    public static <S> Condition<S, List<S>> caught(Predicate<? super S> first,
            Predicate<? super S> second, Predicate<? super S>... rest) {
        return caughtPreserving(stages("predicate", first, second, rest).stream()
                .map(predicate -> ObjectCondition.<S>matches(predicate)).toList());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S> Condition<S, List<S>> caught(PreservingStage<? super S> first,
            PreservingStage<? super S> second, PreservingStage<? super S>... rest) {
        return caughtPreserving(stages("condition", first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, R> Condition<S, List<R>> caught(ResultStage<S, R> first,
            ResultStage<S, R> second, ResultStage<S, R>... rest) {
        List<ResultStage<S, R>> stages = stages("condition", first, second, rest);
        return condition("conditions are satisfied in order", () ->
                new CaughtEvaluator<>(stages.stream()
                        .map(stage -> caughtStage(stage, stage.newEvaluator())).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, F extends Source<?>> SelectedSequenceCondition<S, F> caught(SelectedStage<? super S, F> first,
            SelectedStage<? super S, F> second,
            SelectedStage<? super S, F>... rest) {
        List<SelectedStage<? super S, F>> stages =
                stages("condition", first, second, rest);
        return new RuntimeSelectedSequenceCondition<>("conditions are satisfied in order", null,
                () -> new CaughtEvaluator<>(stages.stream()
                        .map(stage -> caughtStage(stage, stage.newEvaluator())).toList()));
    }

    private static <S> Condition<S, List<S>> caughtPreserving(List<? extends PreservingStage<? super S>> stages) {
        return condition("conditions are satisfied in order", () ->
                new CaughtEvaluator<>(stages.stream()
                        .map(stage -> caughtStage(stage,
                                ConditionRuntime.<S>preservingEvaluator(stage))).toList()));
    }

    private static <S, R> CaughtEvaluator.Stage<S, R> caughtStage(ConditionStage<?, ?> stage,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator) {
        return new CaughtEvaluator.Stage<>(evaluator, stage.description(), stage.explanation());
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

    public static <S, F extends Source<?>> SelectedCondition<S, F> selected(String description,
            Function<? super S, ? extends Evaluation<?>> evaluator) {
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeSelectedCondition<>(nonBlank(description, "description"), null, () -> evaluator);
    }

    @SuppressWarnings("unchecked")
    public static <S, R> Function<S, Evaluation<R>> selectedEvaluator(ConditionStage<? super S, ?> condition) {
        return (Function<S, Evaluation<R>>) (Function<?, ?>) requireNonNull(condition,
                "condition must not be null").newEvaluator();
    }

    public static <S> Function<S, Evaluation<S>> preservingEvaluator(PreservingStage<? super S> condition) {
        Function<? super S, ? extends Evaluation<?>> evaluator = requireNonNull(condition,
                "condition must not be null").newEvaluator();
        return actual -> {
            Evaluation<?> evaluation = evaluator.apply(actual);
            return evaluation == null ? null : evaluation.continueIfSatisfied(ignored -> satisfied(actual));
        };
    }

    public static <S, R> ResultStage<S, R> explained(Condition<S, R> condition,
            String explanation) {
        requireNonNull(condition, "condition must not be null");
        return new RuntimeCondition<>(condition.description(),
                nonBlank(explanation, "explanation"), condition.evaluatorFactory());
    }

    public static <S> PreservingStage<S> explained(PreservingCondition<S> condition, String explanation) {
        requireNonNull(condition, "condition must not be null");
        return new RuntimePreservingCondition<>(condition.description(),
                nonBlank(explanation, "explanation"), condition.evaluatorFactory());
    }

    public static <S, F extends Source<?>> SelectedStage<S, F> explained(SelectedCondition<S, F> condition, String explanation) {
        requireNonNull(condition, "condition must not be null");
        return new RuntimeSelectedCondition<>(condition.description(),
                nonBlank(explanation, "explanation"), condition.evaluatorFactory());
    }

    public static <S, F extends Source<?>> SelectedSequenceStage<S, F> explained(SelectedSequenceCondition<S, F> condition, String explanation) {
        requireNonNull(condition, "condition must not be null");
        return new RuntimeSelectedSequenceCondition<>(condition.description(),
                nonBlank(explanation, "explanation"), condition.evaluatorFactory());
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
