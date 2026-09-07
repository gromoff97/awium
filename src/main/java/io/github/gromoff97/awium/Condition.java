package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

/**
 * Typed, immutable condition. Its family preserves, checks, narrows, selects, or transforms the observed value.
 *
 * @param <Observed> value supplied to the condition
 * @param <Result> value returned when the condition is satisfied
 */
public final class Condition<Observed, Result> extends ConditionDefinition<Observed, Result> {

    Condition(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        super(metadata, evaluatorFactory);
    }

    public Condition<Observed, Result> because(String explanation) {
        return new Condition<>(metadata.because(explanation), evaluatorFactory);
    }

    public Condition<Observed, Result> because(String format, Object... arguments) {
        return because(formattedExplanation(format, arguments));
    }

    public static final class PreservingCondition<Observed> extends ConditionDefinition<Observed, Observed> {

        PreservingCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        public PreservingCondition<Observed> because(String explanation) {
            return new PreservingCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        public PreservingCondition<Observed> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class ExpectedCondition<Expected> extends ConditionDefinition<Object, Object> {

        ExpectedCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super Object, ? extends ConditionResult<? extends Object>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        public ExpectedCondition<Expected> because(String explanation) {
            return new ExpectedCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        public ExpectedCondition<Expected> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class NarrowingCondition<Result> extends ConditionDefinition<Object, Result> {

        NarrowingCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super Object, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        public NarrowingCondition<Result> because(String explanation) {
            return new NarrowingCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        public NarrowingCondition<Result> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class SelectedCondition<Observed, Family extends Source<?>> extends ConditionDefinition<Observed, Object> {

        SelectedCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Object>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        public SelectedCondition<Observed, Family> because(String explanation) {
            return new SelectedCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        public SelectedCondition<Observed, Family> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }

        // The await facade binds Element to this condition's source family.
        @SuppressWarnings("unchecked")
        <Element> ConditionSession<Observed, Element> selectionSession() {
            return (ConditionSession<Observed, Element>) (ConditionSession<?, ?>) newSession();
        }
    }
}
