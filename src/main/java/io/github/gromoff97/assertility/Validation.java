package io.github.gromoff97.assertility;

import java.util.Objects;

final class Validation {

    private Validation() {
    }

    static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
