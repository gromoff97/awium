package io.github.gromoff97.assertility.api;

public interface BooleanTerminals<R> extends ObjectTerminals<Boolean, R> {
    R isTrue();

    R isFalse();
}
