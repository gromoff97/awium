package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static java.lang.Long.parseLong;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.condition.*;
import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AssertionAdapterTest {

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() throws Exception {
        var invocations = new int[1];
        var condition = Conditions.<String, Long>condition(
                "payment id", value -> {
                    invocations[0]++;
                    return satisfied(parseLong(value));
                });

        ConditionEvaluation<Long> evaluation = evaluate(condition, "42");

        assertEquals(SATISFIED, evaluation.status());
        assertEquals(42L, result(evaluation));
        assertEquals(1, invocations[0]);
        assertEquals("payment id", description(condition));
    }

    @Test
    void namedConditionValidatesItsArgumentsAndPreservesNullEvaluation()
            throws Exception {
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition(null, payment -> satisfied(payment)))
                .getMessage().contains("description"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition(null, null))
                .getMessage().contains("description"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> condition(" \n ", payment -> satisfied(payment)))
                .getMessage().contains("description"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> condition("payment", null))
                .getMessage().contains("evaluation"));

        var condition = Conditions.<String, String>condition(
                "nullable evaluation", value -> null);
        assertNull(evaluate(condition, "42"));
    }

    @Test
    void assertedWithoutResultReturnsTheExactActualAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var actual = new String("42");
        var condition = Conditions.<String>asserted(value -> {
            invocations[0]++;
        });

        ConditionEvaluation<String> evaluation = evaluate(condition, actual);

        assertEquals(SATISFIED, evaluation.status());
        assertSame(actual, result(evaluation));
        assertEquals(1, invocations[0]);
        assertTrue(!description(condition).isBlank());
    }

    @Test
    void yieldsMayReturnNullAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var condition = Conditions.<String, String>yields(value -> {
            invocations[0]++;
            return null;
        });

        ConditionEvaluation<String> evaluation = evaluate(condition, "42");

        assertEquals(SATISFIED, evaluation.status());
        assertNull(result(evaluation));
        assertEquals(1, invocations[0]);
        assertTrue(!description(condition).isBlank());
    }

    @Test
    void yieldsAssertionStopsImmediatelyWithItsOriginalCauseAndStackTrace() {
        var time = new FakeTime(0);
        var assertion = new AssertionError("selection failed");
        var frame = new StackTraceElement("PaymentSelector", "select",
                "PaymentSelector.java", 42);
        assertion.setStackTrace(new StackTraceElement[]{frame});
        var invocations = new int[1];

        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class, () -> timedAwait(
                (Source<String>) () -> "42",
                defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(10)), time, time).until(Conditions.<String, Long>yields(value -> {
                            invocations[0]++;
                            throw assertion;
                        }).because("payment selection")));

        assertEquals(1, invocations[0]);
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame},
                failure.getCause().getStackTrace());
    }

    @Test
    void runtimeExceptionEscapesYieldsUnchanged() {
        var failure = new RuntimeException("runtime");
        var condition = Conditions.<String, Long>yields(value -> {
            throw failure;
        });

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> evaluate(condition, "42")));
    }

    @Test
    void nonAssertionErrorEscapesYieldsUnchanged() {
        var failure = new LinkageError("error");

        var condition = Conditions.<String, Long>yields(value -> {
            throw failure;
        });

        assertSame(failure, assertThrows(LinkageError.class,
                () -> evaluate(condition, "42")));
    }

    @Test
    void callbackFactoriesRejectNullCallbacks() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> asserted((Consumer<String>) null))
                .getMessage().contains("assertion"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> yields((Function<String, String>) null))
                .getMessage().contains("callback"));
    }
}
