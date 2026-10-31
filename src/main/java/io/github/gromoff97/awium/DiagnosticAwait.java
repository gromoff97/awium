package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

public sealed class DiagnosticAwait<OBSERVED> extends AwaitSupport<OBSERVED> permits DiagnosticSelectionAwait {

    DiagnosticAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    public DiagnosticAwait<OBSERVED> every(Duration interval) {
        return new DiagnosticAwait<>(source, configuration.withEvery(interval));
    }

    public DiagnosticAwait<OBSERVED> upTo(Duration timeout) {
        return new DiagnosticAwait<>(source, configuration.withUpTo(timeout));
    }

    public DiagnosticAwait<OBSERVED> persisting(Duration persistence) {
        return new DiagnosticAwait<>(source, configuration.withPersistence(persistence));
    }

    DiagnosticAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(source, configuration.withTime(clock, parker));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            AwaitResult<OBSERVED, OBSERVED> until(CHECK condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public AwaitResult<OBSERVED, List<OBSERVED>> until(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return capture(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }
}
