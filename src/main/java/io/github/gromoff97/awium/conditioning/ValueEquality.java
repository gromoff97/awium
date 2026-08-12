package io.github.gromoff97.awium.conditioning;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;

public final class ValueEquality {

    private ValueEquality() {
        throw new AssertionError("Utility class");
    }

    public static boolean equal(Object actual, Object expected) {
        var pending = new ArrayDeque<ValuePair>();
        var visited = new HashSet<ValuePair>();
        pending.addLast(new ValuePair(actual, expected));

        while (!pending.isEmpty()) {
            ValuePair pair = pending.removeLast();
            Object left = pair.actual();
            Object right = pair.expected();
            if (left == right) {
                continue;
            }
            if (left == null || right == null) {
                return false;
            }

            if (left.getClass().isArray() != right.getClass().isArray()) {
                return false;
            }

            if (left instanceof Object[] leftObjects) {
                if (!(right instanceof Object[] rightObjects)) {
                    return false;
                }
                if (!visited.add(pair)) {
                    continue;
                }
                if (leftObjects.length != rightObjects.length) {
                    return false;
                }
                for (int index = leftObjects.length - 1; index >= 0; index--) {
                    pending.addLast(new ValuePair(
                            leftObjects[index], rightObjects[index]));
                }
                continue;
            }
            if (!Objects.deepEquals(left, right)) {
                return false;
            }
        }
        return true;
    }

    private record ValuePair(Object actual, Object expected) {
    }
}
