package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.*;

import java.util.Locale;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Immutable definition; each session owns its lazy callback and execution state. */
abstract sealed class ConditionDefinition<Observed, Result>
        permits Condition, PreservingCondition, ExpectedCondition, NarrowingCondition, SelectedCondition {

    final ConditionMetadata metadata;
    final Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory;

    ConditionDefinition(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        this.metadata = requireNonNull(metadata, "metadata must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    final ConditionSession<Observed, Result> newSession() {
        return new ConditionSession<>(metadata, evaluatorFactory);
    }

    final <Actual extends Observed> ConditionSession<Actual, Actual> preservingSession() {
        var session = newSession();
        return new ConditionSession<>(metadata, () -> actual -> {
            var result = session.apply(actual);
            return result == null ? null : result.mapSatisfied(ignored -> actual);
        });
    }

    static <C extends ConditionDefinition<?, ?>> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }
}
