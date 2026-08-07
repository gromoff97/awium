package io.github.gromoff97.assertility;

import java.util.function.Supplier;

record Evaluation<R>(Supplier<? extends R> resolver) {
    static <R> Evaluation<R> fixed(R value) {
        return new Evaluation<>(() -> value);
    }

    R resolve() {
        return resolver.get();
    }
}
