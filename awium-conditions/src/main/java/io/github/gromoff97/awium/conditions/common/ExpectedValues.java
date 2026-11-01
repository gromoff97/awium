package io.github.gromoff97.awium.conditions.common;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

public final class ExpectedValues {

    private ExpectedValues() {}

    public static <ELEMENT> List<ELEMENT> snapshot(Iterable<ELEMENT> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false).toList();
    }

    public static String text(CharSequence value, String name) {
        return requireNonNull(requireNonNull(value, name + " must not be null").toString(),
                "text.toString() must not return null");
    }

}
