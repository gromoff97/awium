package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.util.concurrent.Callable;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static <OBSERVED> HistoryEntry<OBSERVED> evaluated(HistoryEntry<OBSERVED> attempt) {
        assertTrue(attempt.hasObservation());
        assertFalse(attempt.result() instanceof AttemptStatus.Uncontrolled);
        return attempt;
    }

    static AttemptStatus.Uncontrolled uncontrolled(AttemptStatus.Operation operation, AttemptStatus result) {
        var failure = assertInstanceOf(AttemptStatus.Uncontrolled.class, result);
        assertEquals(operation, failure.operation());
        return failure;
    }

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence, WaitConfiguration.DEFAULT.historyTail(),
                WaitConfiguration.DEFAULT.clock(), WaitConfiguration.DEFAULT.parker());
    }

    static <S> ValueAwait<S> timedAwait(Callable<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    static <S> DiagnosticAwait<S> timedTryAwait(Callable<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(source, configuration.withTime(clock, parker));
    }

    static <OBSERVED> DiagnosticAwait<OBSERVED> diagnostic(ValueAwait<OBSERVED> waiting) {
        return new DiagnosticAwait<>(waiting.source, waiting.configuration);
    }

    static <OBSERVED, ELEMENT> DiagnosticSelectionAwait<OBSERVED, ELEMENT> diagnostic(
            SelectionAwait<OBSERVED, ELEMENT> waiting) {
        return new DiagnosticSelectionAwait<>(waiting.source, waiting.configuration);
    }

    static <OBSERVED extends Map<?, ?>, ENTRY extends Map.Entry<?, ?>, VALUE>
            DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> diagnostic(MapAwait<OBSERVED, ENTRY, VALUE> waiting) {
        return new DiagnosticMapAwait<>(waiting.source, waiting.configuration);
    }
}
