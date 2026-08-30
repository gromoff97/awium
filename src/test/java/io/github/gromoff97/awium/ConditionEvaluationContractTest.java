package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.ConditionEvaluation;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.*;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.UNCONTROLLED;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionEvaluationContractTest {

    @Test
    void evaluationStatesCarryOnlyConditionData() {
        assertEquals(List.of("result"), componentNames(ConditionEvaluation.Satisfied.class));
        assertEquals(List.of("mismatch"), componentNames(ConditionEvaluation.Unsatisfied.class));
        assertEquals(List.of("mismatch", "cause"), componentNames(ConditionEvaluation.AssertionUnsatisfied.class));
        assertEquals(List.of("cause"), componentNames(ConditionEvaluation.Uncontrolled.class));
    }

    @Test
    void satisfiedEvaluationContinuesWithAnotherResultType() throws Exception {
        ConditionEvaluation<String> continued = satisfied(42)
                .continueIfSatisfied(value -> satisfied("value=" + value));

        var result = assertInstanceOf(ConditionEvaluation.Satisfied.class, continued);
        assertEquals("value=42", result.result());
    }

    @Test
    void nonSatisfiedEvaluationSkipsContinuationAndKeepsDiagnostics() throws Exception {
        var assertion = new AssertionError("assertion");
        ConditionEvaluation<String> unsatisfied = ConditionEvaluation.<Integer>assertionUnsatisfied("mismatch", assertion)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });
        var cause = new IllegalStateException("broken");
        ConditionEvaluation<String> uncontrolled = ConditionEvaluation.<Integer>uncontrolled(cause)
                .continueIfSatisfied(value -> {
                    throw new AssertionError("continuation must not run");
                });

        var assertionFailure = assertInstanceOf(ConditionEvaluation.AssertionUnsatisfied.class, unsatisfied);
        assertEquals(UNSATISFIED, assertionFailure.status());
        assertEquals("mismatch", assertionFailure.mismatch());
        assertSame(assertion, assertionFailure.cause());
        var uncontrolledFailure = assertInstanceOf(ConditionEvaluation.Uncontrolled.class, uncontrolled);
        assertEquals(UNCONTROLLED, uncontrolledFailure.status());
        assertSame(cause, uncontrolledFailure.cause());
    }

    @Test
    void unsatisfiedValidatesMismatch() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> unsatisfied(null)).getMessage().contains("mismatch"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> unsatisfied("  \n")).getMessage().contains("mismatch"));
    }

    @Test
    void internalOutcomesRejectNullCauses() {
        assertThrows(NullPointerException.class,
                () -> assertionUnsatisfied("failed", null));
        assertThrows(NullPointerException.class, () -> uncontrolled(null));
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

}
