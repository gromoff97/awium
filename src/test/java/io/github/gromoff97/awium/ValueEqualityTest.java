package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new Object[]{"different", new ThrowingEquals(() -> { throw new IllegalStateException("comparison continued"); })},
                new Object[]{"expected", new Object()}));
    }

    @Test
    void reportsJdkComparisonFailureForDistinctCyclicArrays() {
        Object[] leftFirst = {"first", null};
        Object[] leftSecond = {"second", leftFirst};
        leftFirst[1] = leftSecond;

        Object[] rightFirst = {"first", null};
        Object[] rightSecond = {"second", rightFirst};
        rightFirst[1] = rightSecond;
        var result = tryAwait(() -> leftFirst).until(equalTo(rightFirst));
        var failure = failure(CONDITION_FAILED, result);
        assertInstanceOf(StackOverflowError.class, failure.cause());
        assertEquals(1, attempts(result).size());
        assertTrue(equal(leftFirst, leftFirst));

        rightSecond[0] = "different";
        assertFalse(equal(leftFirst, rightFirst));
    }

    @Test
    void comparesNestedArraysUsingStandardJdkSemantics() {
        Object left = "leaf";
        Object right = "leaf";
        for (int index = 0; index < 64; index++) {
            left = new Object[]{left};
            right = new Object[]{right};
        }

        assertTrue(equal(left, right));
    }

    private static boolean equal(Object actual, Object expected) {
        var evaluation = evaluate(equalTo(expected), actual);
        return evaluation instanceof Satisfied<?>;
    }
}
