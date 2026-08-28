package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.engine.ConditionAssessment;
import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition.ExpectedCondition;
import io.github.gromoff97.awium.fluent.Condition.ExpectedSequenceCondition;
import io.github.gromoff97.awium.fluent.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedStage;
import io.github.gromoff97.awium.fluent.Condition.NarrowingCondition;
import io.github.gromoff97.awium.fluent.Condition.NarrowingStage;
import io.github.gromoff97.awium.fluent.Condition.PreservingCondition;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedCondition;
import io.github.gromoff97.awium.fluent.Condition.SelectedSequenceCondition;
import io.github.gromoff97.awium.fluent.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedStage;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.sources.Source;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static java.util.Objects.requireNonNull;

final class ConditionRuntime {

    private interface RuntimeStage<Observed, Result> {

        String description();

        String explanation();

        Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends Result>>> evaluatorFactory();

        default Function<? super Observed, ? extends ConditionAssessment<? extends Result>> newEvaluator() {
            var evaluator = requireNonNull(evaluatorFactory().get(), "evaluator must not be null");
            return actual -> requireNonNull(evaluator.apply(actual), "condition returned null ConditionAssessment");
        }
    }

    record RuntimeCondition<Observed, Result>(String description, String explanation,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends Result>>> evaluatorFactory)
            implements Condition<Observed, Result>, RuntimeStage<Observed, Result> {
    }

    record RuntimePreservingCondition<Observed>(String description, String explanation,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends Observed>>> evaluatorFactory)
            implements PreservingCondition<Observed>, RuntimeStage<Observed, Observed> {
    }

    record RuntimeExpectedCondition<Expected>(String description, String explanation,
            Supplier<? extends Function<? super Object, ? extends ConditionAssessment<? extends Object>>> evaluatorFactory)
            implements ExpectedCondition<Expected>, RuntimeStage<Object, Object> {
    }

    record RuntimeExpectedSequenceCondition<Expected>(String description, String explanation,
            Supplier<? extends Function<? super Object, ? extends ConditionAssessment<? extends List<Object>>>> evaluatorFactory)
            implements ExpectedSequenceCondition<Expected>, RuntimeStage<Object, List<Object>> {
    }

    record RuntimeNarrowingCondition<Result>(String description, String explanation,
            Supplier<? extends Function<? super Object, ? extends ConditionAssessment<? extends Result>>> evaluatorFactory)
            implements NarrowingCondition<Result>, RuntimeStage<Object, Result> {
    }

    record RuntimeSelectedCondition<Observed, Family extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<?>>> evaluatorFactory)
            implements SelectedCondition<Observed, Family>, RuntimeStage<Observed, Object> {
    }

    record RuntimeSelectedSequenceCondition<Observed, Family extends Source<?>>(String description, String explanation,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends List<Object>>>> evaluatorFactory)
            implements SelectedSequenceCondition<Observed, Family>, RuntimeStage<Observed, List<Object>> {
    }

    static <Observed, Result> Condition<Observed, Result> assessedCondition(String description,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends Result>>> evaluatorFactory) {
        return assessedCondition(description, null, evaluatorFactory);
    }

    static <Observed, Result> Condition<Observed, Result> assessedCondition(String description, String explanation,
            Supplier<? extends Function<? super Observed, ? extends ConditionAssessment<? extends Result>>> evaluatorFactory) {
        return new RuntimeCondition<>(nonBlank(description, "description"), explanation,
                requireNonNull(evaluatorFactory, "evaluator factory must not be null"));
    }

    static <Observed, Result> Condition<Observed, Result> condition(String description,
            Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluation) {
        return new RuntimeCondition<>(nonBlank(description, "description"), null,
                assessments(evaluation));
    }

    static <Observed, Result> Condition<Observed, Result> conditionFactory(String description,
            Supplier<? extends Function<? super Observed, ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory) {
        return new RuntimeCondition<>(nonBlank(description, "description"), null, assessments(evaluatorFactory));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed> Condition<Observed, List<Observed>> captured(Predicate<? super Observed> first,
            Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return capturedPreserving(stages("predicate", first, second, rest).stream()
                .map(predicate -> Conditions.<Observed>matches(predicate)).toList());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed> Condition<Observed, List<Observed>> captured(PreservingStage<? super Observed> first,
            PreservingStage<? super Observed> second, PreservingStage<? super Observed>... rest) {
        return capturedPreserving(stages("condition", first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Result> Condition<Observed, List<Result>> captured(ResultStage<Observed, Result> first,
            ResultStage<Observed, Result> second, ResultStage<Observed, Result>... rest) {
        List<ResultStage<Observed, Result>> stages = stages("condition", first, second, rest);
        return assessedCondition("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<Observed, Result>evaluator(stage))).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Expected> ExpectedSequenceCondition<Expected> captured(ExpectedStage<? extends Expected> first,
            ExpectedStage<? extends Expected> second, ExpectedStage<? extends Expected>... rest) {
        List<ExpectedStage<? extends Expected>> stages = stages("condition", first, second, rest);
        return new RuntimeExpectedSequenceCondition<>("conditions are satisfied in order", null,
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, ConditionRuntime.<Object>expectedEvaluator(stage))).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Family extends Source<?>> SelectedSequenceCondition<Observed, Family> captured(SelectedStage<? super Observed, Family> first,
            SelectedStage<? super Observed, Family> second,
            SelectedStage<? super Observed, Family>... rest) {
        List<SelectedStage<? super Observed, Family>> stages =
                stages("condition", first, second, rest);
        return new RuntimeSelectedSequenceCondition<>("conditions are satisfied in order", null,
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage, selectedEvaluator(stage))).toList()));
    }

    private static <Observed> Condition<Observed, List<Observed>> capturedPreserving(List<? extends PreservingStage<? super Observed>> stages) {
        return assessedCondition("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> capturedStage(stage,
                                ConditionRuntime.<Observed>preservingEvaluator(stage))).toList()));
    }

    private static <Observed, Result> CapturedEvaluator.Stage<Observed, Result> capturedStage(AwaitCondition stage,
            Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator) {
        return new CapturedEvaluator.Stage<>(evaluator, description(stage), explanation(stage));
    }

    private static <Stage> List<Stage> stages(String name, Stage first, Stage second, Stage[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, name + "s must not be null")))
                .map(stage -> requireNonNull(stage, name + " must not be null"))
                .toList();
    }

    static <Observed> PreservingCondition<Observed> preserving(String description,
            Function<? super Observed, ? extends ConditionEvaluation<? extends Observed>> evaluator) {
        return new RuntimePreservingCondition<>(nonBlank(description, "description"), null,
                assessments(evaluator));
    }

    static <Observed> PreservingCondition<Observed> preserving(String description,
            Supplier<? extends Function<? super Observed, ? extends ConditionEvaluation<? extends Observed>>> evaluatorFactory) {
        return new RuntimePreservingCondition<>(nonBlank(description, "description"), null, assessments(evaluatorFactory));
    }

    static <Expected> ExpectedCondition<Expected> expected(String description, Function<Object, ConditionEvaluation<Object>> evaluator) {
        return new RuntimeExpectedCondition<>(nonBlank(description, "description"), null, assessments(evaluator));
    }

    static <Result> NarrowingCondition<Result> narrowing(String description, Function<Object, ConditionEvaluation<Result>> evaluator) {
        return new RuntimeNarrowingCondition<>(nonBlank(description, "description"), null, assessments(evaluator));
    }

    static <Observed, Family extends Source<?>> SelectedCondition<Observed, Family> selected(String description,
            Function<? super Observed, ? extends ConditionEvaluation<?>> evaluator) {
        return new RuntimeSelectedCondition<>(nonBlank(description, "description"), null, assessments(evaluator));
    }

    @SuppressWarnings("unchecked")
    static <Observed, Result> Function<Observed, ConditionAssessment<Result>> selectedEvaluator(AwaitCondition condition) {
        return (Function<Observed, ConditionAssessment<Result>>) (Function<?, ?>)
                ConditionRuntime.<Observed, Result>runtime(condition).newEvaluator();
    }

    static <Observed, Result> Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator(ConditionStage<? super Observed,
            ? extends Result> condition) {
        return ConditionRuntime.<Observed, Result>runtime(condition).newEvaluator();
    }

    static <Observed> Function<Observed, ConditionAssessment<Observed>> preservingEvaluator(PreservingStage<? super Observed> condition) {
        Function<? super Observed, ? extends ConditionAssessment<?>> evaluator = ConditionRuntime.<Observed, Observed>runtime(condition).newEvaluator();
        return actual -> evaluator.apply(actual).mapEvaluation(ignored -> satisfied(actual));
    }

    static <Observed> Function<Observed, ConditionAssessment<Observed>> expectedEvaluator(ExpectedStage<?> condition) {
        Function<? super Object, ? extends ConditionAssessment<?>> evaluator = ConditionRuntime.<Object, Object>runtime(condition).newEvaluator();
        return actual -> evaluator.apply(actual).mapEvaluation(ignored -> satisfied(actual));
    }

    @SuppressWarnings("unchecked")
    static <Observed> Function<Observed, ConditionAssessment<List<Observed>>> expectedSequenceEvaluator(ExpectedSequenceStage<?> condition) {
        return (Function<Observed, ConditionAssessment<List<Observed>>>) (Function<?, ?>)
                ConditionRuntime.<Object, List<Object>>runtime(condition).newEvaluator();
    }

    static <Observed, Result> Function<Observed, ConditionAssessment<Result>> narrowingEvaluator(NarrowingStage<Result> condition) {
        Function<? super Object, ? extends ConditionAssessment<? extends Result>> evaluator =
                ConditionRuntime.<Object, Result>runtime(condition).newEvaluator();
        return actual -> evaluator.apply(actual).mapEvaluation(ConditionEvaluation::satisfied);
    }

    static <Observed, Result> ResultStage<Observed, Result> explained(Condition<Observed, Result> condition,
            String explanation) {
        RuntimeStage<Observed, Result> runtime = runtime(condition);
        return new RuntimeCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Observed> PreservingStage<Observed> explained(PreservingCondition<Observed> condition, String explanation) {
        RuntimeStage<Observed, Observed> runtime = runtime(condition);
        return new RuntimePreservingCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Expected> ExpectedStage<Expected> explained(ExpectedCondition<Expected> condition, String explanation) {
        RuntimeStage<Object, Object> runtime = runtime(condition);
        return new RuntimeExpectedCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Expected> ExpectedSequenceStage<Expected> explained(ExpectedSequenceCondition<Expected> condition, String explanation) {
        RuntimeStage<Object, List<Object>> runtime = runtime(condition);
        return new RuntimeExpectedSequenceCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Result> NarrowingStage<Result> explained(NarrowingCondition<Result> condition, String explanation) {
        RuntimeStage<Object, Result> runtime = runtime(condition);
        return new RuntimeNarrowingCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Observed, Family extends Source<?>> SelectedStage<Observed, Family> explained(SelectedCondition<Observed, Family> condition,
            String explanation) {
        RuntimeStage<Observed, Object> runtime = runtime(condition);
        return new RuntimeSelectedCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static <Observed, Family extends Source<?>>
            SelectedSequenceStage<Observed, Family> explained(SelectedSequenceCondition<Observed, Family> condition,
                    String explanation) {
        RuntimeStage<Observed, List<Object>> runtime = runtime(condition);
        return new RuntimeSelectedSequenceCondition<>(runtime.description(), nonBlank(explanation, "explanation"), runtime.evaluatorFactory());
    }

    static String description(AwaitCondition condition) {
        return runtime(condition).description();
    }

    static String explanation(AwaitCondition condition) {
        return runtime(condition).explanation();
    }

    @SuppressWarnings("unchecked")
    private static <Observed, Result> RuntimeStage<Observed, Result> runtime(AwaitCondition condition) {
        return (RuntimeStage<Observed, Result>) requireNonNull(condition, "condition must not be null");
    }

    private static <Observed, Result> Supplier<? extends Function<? super Observed,
            ? extends ConditionAssessment<? extends Result>>> assessments(Supplier<? extends Function<? super Observed,
                    ? extends ConditionEvaluation<? extends Result>>> evaluatorFactory) {
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return () -> {
            Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluator =
                    requireNonNull(evaluatorFactory.get(), "evaluator must not be null");
            return actual -> ConditionAssessment.plain(evaluator.apply(actual));
        };
    }

    private static <Observed, Result> Supplier<? extends Function<? super Observed,
            ? extends ConditionAssessment<? extends Result>>> assessments(Function<? super Observed,
                    ? extends ConditionEvaluation<? extends Result>> evaluator) {
        requireNonNull(evaluator, "evaluation must not be null");
        return assessments(() -> evaluator);
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
