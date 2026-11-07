package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.util.concurrent.Callable;

public final class Await {
    private Await() {}

    public static <OBSERVED> ValueAwait<OBSERVED> await(Callable<? extends OBSERVED> source) {
        return new ValueAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <OBSERVED> DiagnosticAwait<OBSERVED> tryAwait(Callable<? extends OBSERVED> source) {
        return new DiagnosticAwait<>(source, WaitConfiguration.DEFAULT);
    }
}
