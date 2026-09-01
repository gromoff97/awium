package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValueEqualityTest {

    @Test
    void comparesNullAndNonArrayValuesWithTheActualOperandFirst() {
        var expected = new Directional(false);
        var actual = new Directional(true);

        assertFalse(equal(null, actual));
        assertTrue(equal(actual, expected));
        assertEquals(1, actual.equalsCalls);
    }

    @Test
    void comparesPrimitiveArraysByContent() {
        assertTrue(equal(new int[]{1, 2}, new int[]{1, 2}));
        assertFalse(equal(new int[]{1}, new int[]{2}));
    }

    @Test
    void rejectsArrayKindAndPrimitiveTypeMismatches() {
        assertFalse(equal(new int[]{1, 2}, new long[]{1, 2}));
        assertFalse(equal(new Object[]{1}, new int[]{1}));
        assertFalse(equal(new Object[]{1}, 1));
    }

    @Test
    void comparesNestedObjectArraysAndLeavesActualFirst() {
        var expectedLeaf = new Directional(false);
        var actualLeaf = new Directional(true);

        assertTrue(equal(
                new Object[]{new int[]{1, 2}, new Object[]{actualLeaf}},
                new Object[]{new int[]{1, 2}, new Object[]{expectedLeaf}}));
        assertEquals(1, actualLeaf.equalsCalls);
        assertFalse(equal(
                new Object[]{new int[]{1, 2}},
                new Object[]{new int[]{1, 3}}));
        assertFalse(equal(new Object[]{1}, new Object[]{1, 2}));
    }

    @Test
    void stopsAtTheFirstUnequalObjectArrayElement() {
        assertFalse(equal(
                new Object[]{"different", new ThrowingEquals(
                        new IllegalStateException("comparison continued"))},
                new Object[]{"expected", new Object()}));
    }

    @Test
    void comparesMutualCyclesWithoutRecursion() {
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

    private static boolean equal(Object actual, Object expected) {
        try {
            return evaluate(equalTo(expected), actual).status() == SATISFIED;
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
