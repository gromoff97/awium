package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.CaughtEvaluator;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
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

    private interface Operand<S, R> {
        String description();

        String explanation();

        Supplier<Function<S, Evaluation<R>>> evaluators();
    }

    public record RuntimeCondition<S, R>(String description,
            Supplier<Function<S, Evaluation<R>>> evaluators)
            implements Condition<S, R>, Operand<S, R> {

        @Override
        public String explanation() {
            return null;
        }
    }

    public record RuntimeExplainedCondition<S, R>(String description,
            String explanation, Supplier<Function<S, Evaluation<R>>> evaluators)
            implements ConditionStage<S, R>, Operand<S, R> {
    }

    public record RuntimePreservingCondition<S>(String description,
            Supplier<Function<S, Evaluation<S>>> evaluators)
            implements PreservingCondition<S>, Operand<S, S> {

        @Override
        public String explanation() {
            return null;
        }
    }

    public record RuntimeExplainedPreservingCondition<S>(String description,
            String explanation, Supplier<Function<S, Evaluation<S>>> evaluators)
            implements PreservingStage<S>, Operand<S, S> {
    }

    public record RuntimeSelectedCondition<S, F extends Source<?>>(String description,
            Supplier<Function<S, Evaluation<Object>>> evaluators)
            implements SelectedCondition<S, F>, Operand<S, Object> {

        @Override
        public String explanation() {
            return null;
        }
    }

    public record RuntimeExplainedSelectedCondition<S, F extends Source<?>>(
            String description, String explanation,
            Supplier<Function<S, Evaluation<Object>>> evaluators)
            implements SelectedStage<S, F>, Operand<S, Object> {
    }

    public record RuntimeSelectedSequenceCondition<S, F extends Source<?>>(
            String description,
            Supplier<Function<S, Evaluation<List<Object>>>> evaluators)
            implements SelectedSequenceCondition<S, F>, Operand<S, List<Object>> {

        @Override
        public String explanation() {
            return null;
        }
    }

    public record RuntimeExplainedSelectedSequenceCondition<S, F extends Source<?>>(
            String description, String explanation,
            Supplier<Function<S, Evaluation<List<Object>>>> evaluators)
            implements SelectedSequenceStage<S, F>, Operand<S, List<Object>> {
    }

    public static <S, R> Condition<S, R> condition(String description,
            Supplier<Function<S, Evaluation<R>>> evaluators) {
        return new RuntimeCondition<>(nonBlank(description, "description"),
                requireNonNull(evaluators, "evaluators must not be null"));
    }

    public static <S, R> Condition<S, R> condition(String description,
            Function<? super S, Evaluation<R>> evaluation) {
        String validatedDescription = nonBlank(description, "description");
        requireNonNull(evaluation, "evaluation must not be null");
        Function<S, Evaluation<R>> evaluator = evaluation::apply;
        return new RuntimeCondition<>(validatedDescription, () -> evaluator);
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
    public static <S, R> Condition<S, List<R>> caught(
            ConditionStage<? super S, R> first,
            ConditionStage<? super S, R> second,
            ConditionStage<? super S, R>... rest) {
        List<ConditionStage<? super S, R>> stages =
                stages("condition", first, second, rest);
        return condition("conditions are satisfied in order", () ->
                new CaughtEvaluator<>(stages.stream()
                        .map(stage -> ConditionRuntime.<S, R>evaluator(stage)).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, F extends Source<?>> SelectedSequenceCondition<S, F> caught(
            SelectedStage<? super S, F> first,
            SelectedStage<? super S, F> second,
            SelectedStage<? super S, F>... rest) {
        List<SelectedStage<? super S, F>> stages =
                stages("condition", first, second, rest);
        return new RuntimeSelectedSequenceCondition<>("conditions are satisfied in order",
                () -> new CaughtEvaluator<>(stages.stream()
                        .map(stage -> ConditionRuntime.<S, Object, F>selectedEvaluator(stage))
                        .toList()));
    }

    private static <S> Condition<S, List<S>> caughtPreserving(
            List<? extends PreservingStage<? super S>> stages) {
        return condition("conditions are satisfied in order", () ->
                new CaughtEvaluator<>(stages.stream()
                        .map(stage -> ConditionRuntime.<S>preservingEvaluator(stage)).toList()));
    }

    private static <T> List<T> stages(String name, T first, T second, T[] rest) {
        T[] capturedRest = requireNonNull(rest, name + "s must not be null").clone();
        return Stream.concat(Stream.of(first, second), Arrays.stream(capturedRest))
                .map(stage -> requireNonNull(stage, name + " must not be null"))
                .toList();
    }

    public static <S> PreservingCondition<S> preserving(String description,
            Function<S, Evaluation<S>> evaluator) {
        String validatedDescription = nonBlank(description, "description");
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimePreservingCondition<>(validatedDescription,
                () -> evaluator);
    }

    public static <S, F extends Source<?>> SelectedCondition<S, F> selected(
            String description, Function<S, Evaluation<Object>> evaluator) {
        String validatedDescription = nonBlank(description, "description");
        requireNonNull(evaluator, "evaluation must not be null");
        return new RuntimeSelectedCondition<>(validatedDescription,
                () -> evaluator);
    }

    @SuppressWarnings("unchecked")
    public static <S, R> Function<S, Evaluation<R>> evaluator(
            ConditionStage<? super S, ? extends R> condition) {
        Operand<?, ?> operand = operand(condition);
        return (Function<S, Evaluation<R>>) newEvaluator(operand);
    }

    @SuppressWarnings("unchecked")
    public static <S> Function<S, Evaluation<S>> preservingEvaluator(
            PreservingStage<? super S> condition) {
        Operand<?, ?> operand = operand(condition);
        Function<S, Evaluation<?>> evaluator =
                (Function<S, Evaluation<?>>) newEvaluator(operand);
        return actual -> {
            Evaluation<?> evaluation = evaluator.apply(actual);
            return evaluation == null ? null : evaluation.continueIfSatisfied(
                    ignored -> satisfied(actual));
        };
    }

    @SuppressWarnings("unchecked")
    public static <S, R, F extends Source<?>> Function<S, Evaluation<R>> selectedEvaluator(
            SelectedStage<? super S, F> condition) {
        Operand<?, ?> operand = operand(condition);
        return (Function<S, Evaluation<R>>) newEvaluator(operand);
    }

    @SuppressWarnings("unchecked")
    public static <S, E, F extends Source<?>> Function<S, Evaluation<List<E>>>
            selectedSequenceEvaluator(SelectedSequenceStage<? super S, F> condition) {
        Operand<?, ?> operand = operand(condition);
        return (Function<S, Evaluation<List<E>>>) newEvaluator(operand);
    }

    public static String description(Object condition) {
        return operand(condition).description();
    }

    public static String explanation(Object condition) {
        return operand(condition).explanation();
    }

    public static <S, R> ConditionStage<S, R> explained(Condition<S, R> condition,
            String explanation) {
        Operand<S, R> operand = operand(condition);
        return new RuntimeExplainedCondition<>(operand.description(),
                nonBlank(explanation, "explanation"), operand.evaluators());
    }

    public static <S> PreservingStage<S> explained(
            PreservingCondition<S> condition, String explanation) {
        Operand<S, S> operand = operand(condition);
        return new RuntimeExplainedPreservingCondition<>(operand.description(),
                nonBlank(explanation, "explanation"), operand.evaluators());
    }

    public static <S, F extends Source<?>> SelectedStage<S, F> explained(
            SelectedCondition<S, F> condition, String explanation) {
        Operand<S, Object> operand = operand(condition);
        return new RuntimeExplainedSelectedCondition<>(operand.description(),
                nonBlank(explanation, "explanation"), operand.evaluators());
    }

    public static <S, F extends Source<?>> SelectedSequenceStage<S, F> explained(
            SelectedSequenceCondition<S, F> condition, String explanation) {
        Operand<S, List<Object>> operand = operand(condition);
        return new RuntimeExplainedSelectedSequenceCondition<>(operand.description(),
                nonBlank(explanation, "explanation"), operand.evaluators());
    }

    @SuppressWarnings("unchecked")
    private static <S, R> Operand<S, R> operand(Object condition) {
        return (Operand<S, R>) requireNonNull(condition,
                "condition must not be null");
    }

    private static Function<?, ?> newEvaluator(Operand<?, ?> operand) {
        return requireNonNull(operand.evaluators().get(),
                "evaluator must not be null");
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
