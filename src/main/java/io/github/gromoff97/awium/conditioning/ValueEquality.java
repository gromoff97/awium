package io.github.gromoff97.awium.conditioning;

import static java.lang.System.identityHashCode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ValueEquality {

    private ValueEquality() {
        throw new AssertionError("Utility class");
    }

    public static boolean equal(Object actual, Object expected) {
        Deque<ValuePair> pending = new ArrayDeque<>();
        Set<IdentityArrayPair> visited = new HashSet<>();
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

            boolean leftArray = left.getClass().isArray();
            boolean rightArray = right.getClass().isArray();
            if (!leftArray && !rightArray) {
                if (!Objects.equals(left, right)) {
                    return false;
                }
                continue;
            }
            if (!leftArray || !rightArray) {
                return false;
            }

            if (left instanceof Object[] leftObjects) {
                if (!(right instanceof Object[] rightObjects)) {
                    return false;
                }
                if (!visited.add(new IdentityArrayPair(leftObjects, rightObjects))) {
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

    private record IdentityArrayPair(Object[] actual, Object[] expected) {

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityArrayPair pair
                    && actual == pair.actual && expected == pair.expected;
        }

        @Override
        public int hashCode() {
            return 31 * identityHashCode(actual) + identityHashCode(expected);
        }
    }
}
