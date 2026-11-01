package io.github.gromoff97.awium;

import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static java.util.Objects.requireNonNull;

abstract class Explained<ORIGINAL extends AwaitCondition> implements AwaitCondition {
    protected final ORIGINAL original;
    private final String explanation;

    private Explained(ORIGINAL original, String explanation) {
        this.original = original;
        this.explanation = requireNonNull(explanation, "explanation must not be null");
        if (explanation.isBlank()) throw new IllegalArgumentException("explanation must not be blank");
    }

    @Override public String description() { return original.description(); }
    @Override public List<Reference<?>> references() { return original.references(); }
    @Override public String explanation() { return explanation; }

    private abstract static class Check<INPUT, ORIGINAL extends ApplicableCondition<INPUT, ?>> extends Explained<ORIGINAL> {
        private Check(ORIGINAL original, String explanation) { super(original, explanation); }
        public ConditionResult<?> probe(INPUT actual) throws Throwable { return original.probe(actual); }
        public AwaitAttempt<? super INPUT, ?> newAttempt() throws Exception { return original.newAttempt(); }
        public ConditionRun<INPUT, ?> newRun(AwaitCondition definition, boolean recording) { return original.newRun(definition, recording); }
    }

    static final class Typed<INPUT, RESULT> extends Explained<Condition<INPUT, RESULT>> implements Condition<INPUT, RESULT> {
        Typed(Condition<INPUT, RESULT> original, String explanation) { super(original, explanation); }
        @Override public ConditionResult<RESULT> probe(INPUT actual) throws Throwable { return original.probe(actual); }
        @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return original.newAttempt(); }
        @Override public ConditionRun<INPUT, RESULT> newRun(AwaitCondition definition, boolean recording) { return original.newRun(definition, recording); }
        @Override public Condition<INPUT, RESULT> because(String explanation) { return original.because(explanation); }
    }

    static final class Preserving<INPUT> extends Check<INPUT, PreservingCondition<INPUT>> implements PreservingCondition<INPUT> {
        Preserving(PreservingCondition<INPUT> original, String explanation) { super(original, explanation); }
        @Override public PreservingCondition<INPUT> because(String explanation) { return original.because(explanation); }
    }

    static final class Comparison<EXPECTED> extends Check<Object, ComparisonCondition<EXPECTED>> implements ComparisonCondition<EXPECTED> {
        Comparison(ComparisonCondition<EXPECTED> original, String explanation) { super(original, explanation); }
        @Override public ComparisonCondition<EXPECTED> because(String explanation) { return original.because(explanation); }
    }

    static final class Selected<INPUT> extends Check<INPUT, SelectedCondition<INPUT>> implements SelectedCondition<INPUT> {
        Selected(SelectedCondition<INPUT> original, String explanation) { super(original, explanation); }
        @Override public SelectedCondition<INPUT> because(String explanation) { return original.because(explanation); }
    }

    static final class MapValue<INPUT> extends Check<INPUT, MapValueCondition<INPUT>> implements MapValueCondition<INPUT> {
        MapValue(MapValueCondition<INPUT> original, String explanation) { super(original, explanation); }
        @Override public MapValueCondition<INPUT> because(String explanation) { return original.because(explanation); }
    }

    static final class Collecting<CHECK extends AwaitCondition> extends Explained<CollectingCondition<CHECK>> implements CollectingCondition<CHECK> {
        Collecting(CollectingCondition<CHECK> original, String explanation) { super(original, explanation); }
        @Override public List<AwaitCondition> stages() { return original.stages(); }
        @Override public ConditionRun<?, ?> newRun(AwaitCondition definition, boolean recording) { return original.newRun(definition, recording); }
        @Override public CollectingCondition<CHECK> because(String explanation) { return original.because(explanation); }
    }
}
