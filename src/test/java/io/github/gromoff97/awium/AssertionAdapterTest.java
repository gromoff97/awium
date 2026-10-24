package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionAdapterTest {

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() {
        var invocations = new int[1];
        var condition = Conditions.<String>condition(
                "payment id", value -> {
                    invocations[0]++;
                    return satisfied(value);
                });

        ConditionResult<? extends String> evaluation = evaluate(condition, "42");

        assertEquals(Satisfied.class, evaluation.getClass());
        assertEquals("42", result(evaluation));
        assertEquals(1, invocations[0]);
        assertEquals("payment id", description(condition));
    }

    @Test
    void namedConditionValidatesItsArguments() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition(null, payment -> satisfied(payment)))
                .getMessage().contains("description"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition(null, (CheckedFunction<Object, ConditionResult<Object>>) null))
                .getMessage().contains("description"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> condition(" \n ", payment -> satisfied(payment)))
                .getMessage().contains("description"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition("payment", (CheckedFunction<Object, ConditionResult<Object>>) null))
                .getMessage().contains("evaluation"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition("payment", (Callable<CheckedFunction<Object, ConditionResult<Object>>>) null))
                .getMessage().contains("evaluator factory"));
    }

    @Test
    void assertedWithoutResultReturnsTheExactActualAndInvokesItsCallbackOnce() {
        var invocations = new int[1];
        var actual = new String("42");
        var condition = Conditions.<String>asserted(value -> {
            invocations[0]++;
        });

        ConditionResult<? extends String> evaluation = evaluate(condition, actual);

        assertEquals(Satisfied.class, evaluation.getClass());
        assertSame(actual, result(evaluation));
        assertEquals(1, invocations[0]);
        assertFalse(description(condition).isBlank());
    }

    @Test
    void customAssertionStopsImmediatelyWithItsOriginalCauseAndStackTrace() {
        var time = new FakeTime(0);
        var assertion = new AssertionError("selection failed");
        var frame = new StackTraceElement("PaymentSelector", "select",
                "PaymentSelector.java", 42);
        assertion.setStackTrace(new StackTraceElement[]{frame});
        var invocations = new int[1];

        var failure = assertFailure(CONDITION_FAILED, () -> timedAwait(
                () -> "42",
                DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(10)), time, time).until(Conditions.<String>condition("checked value", value -> {
                            invocations[0]++;
                            throw assertion;
                        }).because("payment selection")));

        assertEquals(1, invocations[0]);
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame},
                failure.getCause().getStackTrace());
    }

    @Test
    void callbackFactoriesRejectNullCallbacks() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> asserted((CheckedConsumer<String>) null))
                .getMessage().contains("assertion"));
    }
}
