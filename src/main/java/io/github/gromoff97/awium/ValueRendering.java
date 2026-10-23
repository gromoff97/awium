package io.github.gromoff97.awium;

import java.util.ArrayDeque;
import java.util.HashSet;

import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

final class ValueRendering {

    private ValueRendering() {}

    static String renderValue(Object value) {
        if (value instanceof Object[] values) {
            return renderArray(values);
        }
        if (value != null && value.getClass().isArray()) {
            String array = deepToString(new Object[]{value});
            return array.substring(1, array.length() - 1);
        }
        return requireNonNull(String.valueOf(value),
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
                out.append(renderValue(value));
            }
        }
        return out.toString();
    }

}
