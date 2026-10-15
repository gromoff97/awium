package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.condition;
import static java.util.Objects.requireNonNull;

/** Creates ordered conditions and owns the captured values of one execution. */
final class CapturedEvaluator<OBSERVED, RESULT> implements CheckedFunction<OBSERVED, ConditionResult<List<RESULT>>> {

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED> Condition<OBSERVED, List<OBSERVED>> captured(PreservingCondition<? super OBSERVED> first,
            PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        List<PreservingCondition<? super OBSERVED>> stages = stages(first, second, rest);
        return condition("conditions are satisfied in order", () ->
                new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<OBSERVED>preservingSession()).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, RESULT> Condition<OBSERVED, List<RESULT>> captured(Condition<? super OBSERVED, ? extends RESULT> first,
            Condition<? super OBSERVED, ? extends RESULT> second, Condition<? super OBSERVED, ? extends RESULT>... rest) {
        List<Condition<? super OBSERVED, ? extends RESULT>> stages = stages(first, second, rest);
        return condition("conditions are satisfied in order", () ->
                new CapturedEvaluator<OBSERVED, RESULT>(stages.stream()
                        .map(stage -> stage.newSession()).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, ELEMENT> Condition<OBSERVED, List<ELEMENT>> captured(SelectedCondition<? super OBSERVED> first,
            SelectedCondition<? super OBSERVED> second,
            SelectedCondition<? super OBSERVED>... rest) {
        List<SelectedCondition<? super OBSERVED>> stages =
                stages(first, second, rest);
        return condition("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<ELEMENT>selectionSession()).toList()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, MAP_VALUE> Condition<OBSERVED, List<MAP_VALUE>> captured(MapValueCondition<? super OBSERVED> first,
            MapValueCondition<? super OBSERVED> second,
            MapValueCondition<? super OBSERVED>... rest) {
        List<MapValueCondition<? super OBSERVED>> stages =
                stages(first, second, rest);
        return condition("conditions are satisfied in order",
                () -> new CapturedEvaluator<>(stages.stream()
                        .map(stage -> stage.<MAP_VALUE>selectionSession()).toList()));
    }

    private static <STAGE> List<STAGE> stages(STAGE first, STAGE second, STAGE[] rest) {
        return Stream.concat(Stream.of(first, second),
                        Arrays.stream(requireNonNull(rest, "conditions must not be null")))
                .map(stage -> requireNonNull(stage, "condition must not be null"))
                .toList();
    }

    private final List<? extends ConditionSession<? super OBSERVED, ? extends RESULT>> stages;
    private final ArrayList<RESULT> results = new ArrayList<>();

    private CapturedEvaluator(List<? extends ConditionSession<? super OBSERVED, ? extends RESULT>> stages) {
        this.stages = stages;
    }

    @Override
    public ConditionResult<List<RESULT>> apply(OBSERVED actual) {
        int evaluatedStageIndex = Math.min(results.size(), stages.size() - 1);
        var evaluation = stages.get(evaluatedStageIndex).apply(actual);
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

    private ConditionResult<List<RESULT>> recordStageResult(RESULT result, int evaluatedStageIndex) {
        if (results.size() == stages.size()) {
            results.set(evaluatedStageIndex, result);
        } else {
            results.add(result);
        }
        if (results.size() < stages.size()) {
            return ConditionResult.<List<RESULT>>unsatisfied(
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
