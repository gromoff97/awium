package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static java.lang.Long.parseLong;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
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
        var condition = Condition.<String, Long>condition(
                "payment id", value -> {
                    invocations[0]++;
                    return satisfied(parseLong(value));
                });

        Evaluation<Long> evaluation = evaluate(condition, "42");

        assertEquals(SATISFIED, evaluation.status());
        assertEquals(42L, evaluation.result());
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

        var condition = Condition.<String, String>condition(
                "nullable evaluation", value -> null);
        assertNull(evaluate(condition, "42"));
    }

    @Test
    void assertedWithoutResultReturnsTheExactActualAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var actual = new String("42");
        var condition = Condition.<String>asserted(value -> {
            invocations[0]++;
        });

        Evaluation<String> evaluation = evaluate(condition, actual);

        assertEquals(SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertEquals(1, invocations[0]);
        assertTrue(!description(condition).isBlank());
    }

    @Test
    void yieldsMayReturnNullAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var condition = Condition.<String, String>yields(value -> {
            invocations[0]++;
            return null;
        });

        Evaluation<String> evaluation = evaluate(condition, "42");

        assertEquals(SATISFIED, evaluation.status());
        assertNull(evaluation.result());
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
                defaults().withEvery(ofNanos(1))
                        .withUpTo(ofNanos(10)), time, time).until(
                        Condition.<String, Long>yields(value -> {
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
        var condition = Condition.<String, Long>yields(value -> {
            throw failure;
        });

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> evaluate(condition, "42")));
    }

    @Test
    void nonAssertionErrorEscapesYieldsUnchanged() {
        var failure = new LinkageError("error");

        var condition = Condition.<String, Long>yields(value -> {
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
