package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class AwaitTestAccess {

    private AwaitTestAccess() {}

    public static <OBSERVED> ValueAwait<OBSERVED> withTime(ValueAwait<OBSERVED> waiting, LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(waiting.source, withTime(waiting.configuration, clock, parker));
    }

    public static <OBSERVED> DiagnosticAwait<OBSERVED> withTime(DiagnosticAwait<OBSERVED> waiting, LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(waiting.source, withTime(waiting.configuration, clock, parker));
    }

    public static <OBSERVED extends Map<?, ?>, ENTRY extends Map.Entry<?, ?>, VALUE> MapAwait<OBSERVED, ENTRY, VALUE> withTime(
            MapAwait<OBSERVED, ENTRY, VALUE> waiting, LongSupplier clock, LongConsumer parker) {
        return new MapAwait<>(waiting.source, withTime(waiting.configuration, clock, parker));
    }

    public static <OBSERVED extends Map<?, ?>, ENTRY extends Map.Entry<?, ?>, VALUE> DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> withTime(
            DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> waiting, LongSupplier clock, LongConsumer parker) {
        return new DiagnosticMapAwait<>(waiting.source, withTime(waiting.configuration, clock, parker));
    }

    public static WaitConfiguration withTime(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new WaitConfiguration(configuration.everyNanos(), configuration.upToNanos(),
                configuration.persistenceNanos(), clock, parker);
    }

    public static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence,
                WaitConfiguration.DEFAULT.clock(), WaitConfiguration.DEFAULT.parker());
    }

    public static <OBSERVED> ValueAwait<OBSERVED> timedAwait(Callable<? extends OBSERVED> source, FakeTime time) {
        return timedAwait(source, WaitConfiguration.DEFAULT, time, time);
    }

    public static <S> ValueAwait<S> timedAwait(Callable<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, withTime(configuration, clock, parker));
    }

    public static <S> DiagnosticAwait<S> timedTryAwait(Callable<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(source, withTime(configuration, clock, parker));
    }

    public static <OBSERVED> DiagnosticAwait<OBSERVED> diagnostic(ValueAwait<OBSERVED> waiting) {
        return new DiagnosticAwait<>(waiting.source, waiting.configuration);
    }
}
