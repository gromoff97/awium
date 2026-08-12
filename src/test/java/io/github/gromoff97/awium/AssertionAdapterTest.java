package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AssertionAdapterTest {

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() throws Exception {
        var invocations = new int[1];
        var condition = ConditionProvider.<String, Long>condition(
                "payment id", value -> {
                    invocations[0]++;
                    return satisfied(Long.parseLong(value));
                });

        Evaluation<Long> evaluation = condition.evaluate("42");

        assertEquals(SATISFIED, evaluation.status());
        assertEquals(42L, evaluation.result());
        assertEquals(1, invocations[0]);
        assertEquals("payment id", condition.description());
    }

    @Test
    void namedConditionValidatesItsArgumentsAndPreservesNullEvaluation()
            throws Exception {
        assertEquals("description must not be null",
                assertThrows(NullPointerException.class,
                        () -> condition(null,
                                payment -> satisfied(payment)))
                        .getMessage());
        assertEquals("description must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> condition(" \n ",
                                payment -> satisfied(payment)))
                        .getMessage());
        assertEquals("evaluation must not be null",
                assertThrows(NullPointerException.class,
                        () -> condition("payment", null))
                        .getMessage());

        var condition = ConditionProvider.<String, String>condition(
                "nullable evaluation", value -> null);
        assertNull(condition.evaluate("42"));
    }

    @Test
    void assertedReturnsTheExactActualAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var actual = new String("42");
        var condition = ConditionProvider.<String>asserted(value -> invocations[0]++);

        Evaluation<String> evaluation = condition.runtime().evaluate(actual);

        assertEquals(SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertEquals(1, invocations[0]);
        assertEquals("assertion to pass", condition.runtime().description().get());
    }

    @Test
    void passedMaySatisfyWithNullAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var condition = ConditionProvider.<String, String>passed(value -> {
            invocations[0]++;
            return null;
        });

        Evaluation<String> evaluation = condition.evaluate("42");

        assertEquals(SATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(1, invocations[0]);
        assertEquals("assertion to pass", condition.description());
    }

    @Test
    void passedReturnsTheFinalSelectedResultAfterDiscardedAssertionRetries() {
        var time = new FakeTime(0);
        var discarded = new MessageReadingAssertion();
        var invocations = new int[1];

        Long result = timedAwait((Source<String>) () -> "42",
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(10)), time, time).until(
                ConditionProvider.<String, Long>passed(value -> {
                    if (invocations[0]++ < 2) {
                        throw discarded;
                    }
                    return Long.parseLong(value);
                }).because("payment selection"));

        assertEquals(42L, result);
        assertEquals(3, invocations[0]);
        assertEquals(java.util.List.of(1L, 1L), time.parkRequests);
    }

    @Test
    void passedPreservesTheCaughtAssertionWithoutReadingItsMessage()
            throws Exception {
        var failure = new MessageReadingAssertion();
        var condition = ConditionProvider.<String, Long>passed(value -> {
            throw failure;
        });
        Evaluation<Long> evaluation = condition.evaluate("42");

        assertEquals("assertion did not pass", evaluation.mismatch());
        assertSame(failure, evaluation.assertionCause());
    }

    @Test
    void checkedExceptionEscapesPassedUnchanged() {
        var failure = new Exception("checked");
        var condition = ConditionProvider.<String, Long>passed(value -> {
            throw failure;
        });

        assertSame(failure, assertThrows(Exception.class,
                () -> condition.evaluate("42")));
    }

    @Test
    void nonAssertionErrorEscapesPassedUnchanged() {
        var failure = new LinkageError("error");

        var condition = ConditionProvider.<String, Long>passed(value -> {
            throw failure;
        });

        assertSame(failure, assertThrows(LinkageError.class,
                () -> condition.evaluate("42")));
    }

    @Test
    void assertionFactoriesRejectNullCallbacks() {
        assertEquals("assertion must not be null",
                assertThrows(NullPointerException.class,
                        () -> asserted(null)).getMessage());
        assertEquals("assertion must not be null",
                assertThrows(NullPointerException.class,
                        () -> passed(null)).getMessage());
    }

    private static final class MessageReadingAssertion extends AssertionError {
        @Override
        public String getMessage() {
            throw new InternalError("message read");
        }
    }
}
