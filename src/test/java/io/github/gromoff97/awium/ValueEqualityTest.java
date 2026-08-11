package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValueEqualityTest {

    @Test
    void comparesNullAndNonArrayValuesWithTheActualOperandFirst() {
        var expected = new RightOperand();
        var actual = new LeftOperand(expected);

        assertTrue(equal(null, null));
        assertFalse(equal(null, actual));
        assertFalse(equal(actual, null));
        assertTrue(equal(actual, expected));
        assertTrue(actual.compared);
        assertFalse(expected.compared);
    }

    @Test
    void comparesEveryPrimitiveArrayTypeByContent() {
        assertTrue(equal(new boolean[]{true}, new boolean[]{true}));
        assertTrue(equal(new byte[]{1}, new byte[]{1}));
        assertTrue(equal(new short[]{1}, new short[]{1}));
        assertTrue(equal(new int[]{1, 2}, new int[]{1, 2}));
        assertTrue(equal(new long[]{1}, new long[]{1}));
        assertTrue(equal(new char[]{'a'}, new char[]{'a'}));
        assertTrue(equal(new float[]{1.5F}, new float[]{1.5F}));
        assertTrue(equal(new double[]{1.5}, new double[]{1.5}));

        assertFalse(equal(new boolean[]{true}, new boolean[]{false}));
        assertFalse(equal(new byte[]{1}, new byte[]{2}));
        assertFalse(equal(new short[]{1}, new short[]{2}));
        assertFalse(equal(new int[]{1}, new int[]{2}));
        assertFalse(equal(new long[]{1}, new long[]{2}));
        assertFalse(equal(new char[]{'a'}, new char[]{'b'}));
        assertFalse(equal(new float[]{1.5F}, new float[]{2.5F}));
        assertFalse(equal(new double[]{1.5}, new double[]{2.5}));
    }

    @Test
    void rejectsArrayKindAndPrimitiveTypeMismatches() {
        assertFalse(equal(new int[]{1, 2}, new long[]{1, 2}));
        assertFalse(equal(new int[]{1}, new Object[]{1}));
        assertFalse(equal(new Object[]{1}, new int[]{1}));
        assertFalse(equal(new Object[]{1}, 1));
        assertFalse(equal(1, new Object[]{1}));
    }

    @Test
    void comparesNestedObjectArraysAndLeavesActualFirst() {
        var expectedLeaf = new RightOperand();
        var actualLeaf = new LeftOperand(expectedLeaf);

        assertTrue(equal(
                new Object[]{new int[]{1, 2}, new Object[]{actualLeaf}},
                new Object[]{new int[]{1, 2}, new Object[]{expectedLeaf}}));
        assertTrue(actualLeaf.compared);
        assertFalse(expectedLeaf.compared);
        assertFalse(equal(
                new Object[]{new int[]{1, 2}},
                new Object[]{new int[]{1, 3}}));
        assertFalse(equal(new Object[]{1}, new Object[]{1, 2}));
    }

    @Test
    void stopsAtTheFirstUnequalObjectArrayElement() {
        assertFalse(equal(
                new Object[]{"different", new FailIfCompared()},
                new Object[]{"expected", new Object()}));
    }

    @Test
    void comparesSelfReferencesAndMutualCyclesWithoutRecursion() {
        Object[] leftSelf = new Object[1];
        Object[] rightSelf = new Object[1];
        leftSelf[0] = leftSelf;
        rightSelf[0] = rightSelf;
        assertTrue(equal(leftSelf, rightSelf));

        Object[] leftFirst = new Object[2];
        Object[] leftSecond = new Object[2];
        leftFirst[0] = "first";
        leftFirst[1] = leftSecond;
        leftSecond[0] = "second";
        leftSecond[1] = leftFirst;

        Object[] rightFirst = new Object[2];
        Object[] rightSecond = new Object[2];
        rightFirst[0] = "first";
        rightFirst[1] = rightSecond;
        rightSecond[0] = "second";
        rightSecond[1] = rightFirst;
        assertTrue(equal(leftFirst, rightFirst));

        rightSecond[0] = "different";
        assertFalse(equal(leftFirst, rightFirst));
    }

    @Test
    void comparesVeryDeepNestingWithoutUsingTheJavaStack() {
        Object left = "leaf";
        Object right = "leaf";
        for (int index = 0; index < 50_000; index++) {
            left = new Object[]{left};
            right = new Object[]{right};
        }

        assertTrue(equal(left, right));
    }

    @SuppressWarnings("overrides")
    private static final class LeftOperand {
        private final Object expected;
        private boolean compared;

        private LeftOperand(Object expected) {
            this.expected = expected;
        }

        @Override
        public boolean equals(Object other) {
            compared = true;
            return other == expected;
        }
    }

    @SuppressWarnings("overrides")
    private static final class RightOperand {
        private boolean compared;

        @Override
        public boolean equals(Object other) {
            compared = true;
            return false;
        }
    }

    @SuppressWarnings("overrides")
    private static final class FailIfCompared {
        @Override
        public boolean equals(Object other) {
            throw new AssertionError("comparison continued after a mismatch");
        }
    }
}
