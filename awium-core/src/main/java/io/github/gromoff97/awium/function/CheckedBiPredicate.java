package io.github.gromoff97.awium.function;

import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface CheckedBiPredicate<FIRST, SECOND> {

    boolean test(FIRST first, SECOND second) throws Throwable;

    static <FIRST, SECOND> CheckedBiPredicate<FIRST, SECOND> not(
            CheckedBiPredicate<? super FIRST, ? super SECOND> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return (first, second) -> !predicate.test(first, second);
    }
}
