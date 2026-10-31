package io.github.gromoff97.awium.conditions.internal;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

final class ExpectedValues {

    private ExpectedValues() {}

    static <ELEMENT> List<ELEMENT> snapshot(Iterable<ELEMENT> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false).toList();
    }

}
