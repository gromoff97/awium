package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static <OBSERVED> AttemptRecord<OBSERVED> evaluated(AttemptRecord<OBSERVED> attempt) {
        assertTrue(attempt.hasObservation());
        assertFalse(attempt.result() instanceof AttemptResult.Uncontrolled);
        return attempt;
    }

    static AttemptResult.Uncontrolled uncontrolled(AttemptResult.Operation operation, AttemptResult result) {
        var failure = assertInstanceOf(AttemptResult.Uncontrolled.class, result);
        assertEquals(operation, failure.operation());
        return failure;
    }

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence,
                WaitConfiguration.DEFAULT.clock(), WaitConfiguration.DEFAULT.parker());
    }

    static <S> ValueAwait<S> timedAwait(Callable<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }
}
