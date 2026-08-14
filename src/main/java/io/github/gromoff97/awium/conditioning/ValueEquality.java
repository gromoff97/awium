package io.github.gromoff97.awium.conditioning;

import java.util.ArrayDeque;
import java.util.HashSet;

import static java.util.Objects.deepEquals;

public final class ValueEquality {

    private ValueEquality() {
        throw new AssertionError("Utility class");
    }

    public static boolean equal(Object actual, Object expected) {
        record ValuePair(Object actual, Object expected) {}

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
            if (left instanceof Object[] leftObjects && right instanceof Object[] rightObjects) {
                if (!visited.add(pair)) {
                    continue;
                }
                if (leftObjects.length != rightObjects.length) {
                    return false;
                }
                for (int index = leftObjects.length - 1; index >= 0; index--) {
                    pending.addLast(new ValuePair(leftObjects[index], rightObjects[index]));
                }
                continue;
            }
            if (!deepEquals(left, right)) {
                return false;
            }
        }
        return true;
    }
}
