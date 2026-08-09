package io.github.gromoff97.assertility;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class ValueEquality {

    private ValueEquality() {
    }

    static boolean equal(Object actual, Object expected) {
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
            if (!primitiveArraysEqual(left, right)) {
                return false;
            }
        }
        return true;
    }

    private static boolean primitiveArraysEqual(Object actual, Object expected) {
        if (actual instanceof boolean[] left) {
            return expected instanceof boolean[] right && Arrays.equals(left, right);
        }
        if (actual instanceof byte[] left) {
            return expected instanceof byte[] right && Arrays.equals(left, right);
        }
        if (actual instanceof short[] left) {
            return expected instanceof short[] right && Arrays.equals(left, right);
        }
        if (actual instanceof int[] left) {
            return expected instanceof int[] right && Arrays.equals(left, right);
        }
        if (actual instanceof long[] left) {
            return expected instanceof long[] right && Arrays.equals(left, right);
        }
        if (actual instanceof char[] left) {
            return expected instanceof char[] right && Arrays.equals(left, right);
        }
        if (actual instanceof float[] left) {
            return expected instanceof float[] right && Arrays.equals(left, right);
        }
        if (actual instanceof double[] left) {
            return expected instanceof double[] right && Arrays.equals(left, right);
        }
        return false;
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
            return 31 * System.identityHashCode(actual)
                    + System.identityHashCode(expected);
        }
    }
}
