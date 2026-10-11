package io.github.gromoff97.awium;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

final class ValueRendering {

    private ValueRendering() {}

    static <Element> List<Element> snapshot(Stream<? extends Element> values) {
        return unmodifiableList(new SnapshotList<>(values.toList()));
    }

    static String renderValue(Object value) {
        if (value instanceof Object[] values) {
            return renderArray(values);
        }
        if (value != null && value.getClass().isArray()) {
            String array = deepToString(new Object[]{value});
            return array.substring(1, array.length() - 1);
        }
        return requireNonNull(stringValue(value),
                "value toString() must not return null");
    }

    private static String renderArray(Object[] values) {
        record ArrayFrame(Object[] values, int index) {}

        var pending = new ArrayDeque<ArrayFrame>();
        var active = new HashSet<Object[]>();
        var out = new StringBuilder("[");
        pending.push(new ArrayFrame(values, 0));
        active.add(values);
        while (!pending.isEmpty()) {
            ArrayFrame frame = pending.pop();
            if (frame.index() == frame.values().length) {
                out.append(']');
                active.remove(frame.values());
                continue;
            }
            if (frame.index() > 0) out.append(", ");
            pending.push(new ArrayFrame(frame.values(), frame.index() + 1));
            Object value = frame.values()[frame.index()];
            if (value instanceof Object[] nested) {
                if (active.add(nested)) {
                    out.append('[');
                    pending.push(new ArrayFrame(nested, 0));
                } else {
                    out.append("[...]");
                }
            } else {
                out.append(value != null && value.getClass().isArray()
                        ? renderValue(value) : stringValue(value));
            }
        }
        return out.toString();
    }

    static String stringValue(Object value) {
        return preservingInterrupt(() -> String.valueOf(value));
    }

    static String preservingInterrupt(Supplier<String> rendering) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return rendering.get();
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    private static final class SnapshotList<Element> extends AbstractList<Element> {
        private final List<? extends Element> values;

        private SnapshotList(List<? extends Element> values) {
            this.values = values;
        }

        @Override
        public Element get(int index) { return values.get(index); }

        @Override
        public int size() { return values.size(); }

        @Override
        public String toString() {
            return values.stream().map(ValueRendering::stringValue).collect(joining(", ", "[", "]"));
        }
    }

}
