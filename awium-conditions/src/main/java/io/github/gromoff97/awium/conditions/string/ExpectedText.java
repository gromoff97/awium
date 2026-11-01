package io.github.gromoff97.awium.conditions.string;

import static java.util.Objects.requireNonNull;

final class ExpectedText {
    private ExpectedText() {}

    static String text(CharSequence value, String name) {
        return requireNonNull(requireNonNull(value, name + " must not be null").toString(),
                "text.toString() must not return null");
    }
}
