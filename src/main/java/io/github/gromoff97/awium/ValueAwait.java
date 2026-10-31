package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

public sealed class ValueAwait<OBSERVED> extends AwaitSupport<OBSERVED> permits SelectionAwait {

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    public ValueAwait<OBSERVED> every(Duration interval) {
        return new ValueAwait<>(source, configuration.withEvery(interval));
    }

    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return new ValueAwait<>(source, configuration.withUpTo(timeout));
    }

    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return new ValueAwait<>(source, configuration.withPersistence(persistence));
    }

    ValueAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            OBSERVED until(CHECK condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> RESULT until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public List<OBSERVED> until(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return complete(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }
}
