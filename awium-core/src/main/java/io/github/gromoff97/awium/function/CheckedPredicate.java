package io.github.gromoff97.awium.function;

import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface CheckedPredicate<VALUE> {

    boolean test(VALUE value) throws Throwable;

    static <VALUE> CheckedPredicate<VALUE> not(CheckedPredicate<? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return value -> !predicate.test(value);
    }
}
