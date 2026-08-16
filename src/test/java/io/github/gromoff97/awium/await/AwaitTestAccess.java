package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

public final class AwaitTestAccess {

    private AwaitTestAccess() {
        throw new AssertionError("Utility class");
    }

    public static <S> Await<S> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }

    public static <S> StructuralAwait<S> timedStructuralAwait(
            Source<? extends S> source, String subject,
            ToIntFunction<? super S> size, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new StructuralAwait<>(source, subject, size, configuration,
                clock, parker);
    }
}
