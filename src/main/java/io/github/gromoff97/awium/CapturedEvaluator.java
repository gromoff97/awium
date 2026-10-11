package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.ConditionSupport.conditionFactory;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

/** Creates ordered conditions and owns the captured values of one execution. */
final class CapturedEvaluator<Observed, Result> implements CheckedFunction<Observed, ConditionResult<List<Result>>> {

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed> Condition<Observed, List<Observed>> captured(PreservingCondition<? super Observed> first,
            PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return capturedPreserving(stages(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Result> Condition<Observed, List<Result>> captured(Condition<? super Observed, ? extends Result> first,
            Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        List<Condition<? super Observed, ? extends Result>> stages = stages(first, second, rest);
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<Observed, Result>(stages.stream()
                        .map(stage -> stage.newSession()).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Expected extends Observed> Condition<Observed, List<Observed>> captured(ExpectedCondition<? extends Expected> first,
            ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        return capturedPreserving(stages(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <Observed, Element, Family> Condition<Observed, List<Element>> captured(SelectedCondition<? super Observed, Family> first,
            SelectedCondition<? super Observed, Family> second,
            SelectedCondition<? super Observed, Family>... rest) {
        List<SelectedCondition<? super Observed, Family>> stages =
                stages(first, second, rest);
        return conditionFactory("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<Element>selectionSession()).toList()));
    }

    private static <Observed> Condition<Observed, List<Observed>> capturedPreserving(List<? extends ConditionDefinition<? super Observed, ?>> stages) {
        return conditionFactory("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<Observed>preservingSession()).toList()));
    }

    private static <Stage> List<Stage> stages(Stage first, Stage second, Stage[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, "conditions must not be null")))
                .map(stage -> requireNonNull(stage, "condition must not be null"))
                .toList();
    }

    private final List<ConditionSession<? super Observed, ? extends Result>> stages;
    private final ArrayList<Result> results = new ArrayList<>();

    CapturedEvaluator(List<? extends ConditionSession<? super Observed, ? extends Result>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public ConditionResult<List<Result>> apply(Observed actual) {
        int evaluatedStageIndex = Math.min(results.size(), stages.size() - 1);
        var evaluation = stages.get(evaluatedStageIndex).apply(actual);
        if (evaluation == null) {
            return ConditionResult.<List<Result>>uncontrolled(
                    new NullPointerException("condition returned null ConditionResult"))
                    .withContext(contextFor(evaluatedStageIndex));
        }
        ConditionResult.Context context = evaluation.context();
        if (context instanceof ConditionResult.Context.Expectation expectation) {
            context = new ConditionResult.Context.Sequence(evaluatedStageIndex, stages.size(), evaluatedStageIndex + 1,
                    expectation.description(), stages.get(evaluatedStageIndex).metadata.explanation(), expectation.reference());
        } else if (evaluation instanceof ConditionResult.Satisfied<?>
                || !(context instanceof ConditionResult.Context.Sequence)) {
            context = contextFor(evaluatedStageIndex);
        }
        return evaluation.withContext(context).continueIfSatisfied(value -> recordStageResult(value, evaluatedStageIndex));
    }

    private ConditionResult<List<Result>> recordStageResult(Result result, int evaluatedStageIndex) {
        if (results.size() == stages.size()) {
            results.set(evaluatedStageIndex, result);
        } else {
            results.add(result);
        }
        if (results.size() < stages.size()) {
            return ConditionResult.<List<Result>>unsatisfied(
                    "waiting for sequence stage " + (results.size() + 1))
                    .withContext(contextFor(results.size(), evaluatedStageIndex));
        }
        return satisfied(results.stream().toList()).withContext(contextFor(results.size(), evaluatedStageIndex));
    }

    private ConditionResult.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private ConditionResult.Context.Sequence contextFor(int capturedStages, int evaluatedStageIndex) {
        ConditionMetadata metadata = stages.get(Math.min(capturedStages, stages.size() - 1)).metadata;
        return new ConditionResult.Context.Sequence(capturedStages, stages.size(), evaluatedStageIndex + 1,
                metadata.description(), metadata.explanation(), metadata.reference());
    }

}
