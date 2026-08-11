package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.engine.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AssertionAdapterTest {

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() throws Exception {
        var invocations = new int[1];
        var condition = ConditionProvider.<Payment, Long>condition(
                "payment id", payment -> {
                    invocations[0]++;
                    return satisfied(payment.id());
                });

        Evaluation<Long> evaluation = condition.evaluate(new Payment(42));

        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
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

        var condition = ConditionProvider.<Payment, Payment>condition(
                "nullable evaluation", payment -> null);
        assertNull(condition.evaluate(new Payment(42)));
    }

    @Test
    void assertedReturnsTheExactActualAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var actual = new Payment(42);
        var condition = ConditionProvider.<Payment>asserted(payment -> invocations[0]++);

        Evaluation<Payment> evaluation = condition.runtime().evaluate(actual);

        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertEquals(1, invocations[0]);
        assertEquals("assertion to pass", condition.runtime().description().get());
    }

    @Test
    void passedMaySatisfyWithNullAndInvokesItsCallbackOnce() throws Exception {
        var invocations = new int[1];
        var condition = ConditionProvider.<Payment, String>passed(payment -> {
            invocations[0]++;
            return null;
        });

        Evaluation<String> evaluation = condition.evaluate(new Payment(42));

        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertEquals(1, invocations[0]);
        assertEquals("assertion to pass", condition.description());
    }

    @Test
    void assertedSucceedsAfterDiscardedAssertionRetriesWithoutRenderingThem() {
        var time = new FakeTime(0);
        var actual = new Payment(42);
        var discarded = new MessageReadingAssertion();
        var invocations = new int[1];

        Payment result = stage(time, actual).until(
                ConditionProvider.<Payment>asserted(payment -> {
                    if (invocations[0]++ < 2) {
                        throw discarded;
                    }
                }).because("payment assertion"));

        assertSame(actual, result);
        assertEquals(3, invocations[0]);
        assertEquals(0, discarded.messageReads);
        assertEquals(java.util.List.of(1L, 1L), time.parkRequests());
    }

    @Test
    void passedReturnsTheFinalSelectedResultAfterDiscardedAssertionRetries() {
        var time = new FakeTime(0);
        var actual = new Payment(42);
        var discarded = new MessageReadingAssertion();
        var invocations = new int[1];

        Long result = stage(time, actual).until(
                ConditionProvider.<Payment, Long>passed(payment -> {
                    if (invocations[0]++ < 2) {
                        throw discarded;
                    }
                    return payment.id();
                }).because("payment selection"));

        assertEquals(42L, result);
        assertEquals(3, invocations[0]);
        assertEquals(0, discarded.messageReads);
        assertEquals(java.util.List.of(1L, 1L), time.parkRequests());
    }

    @Test
    void assertionAdaptersPreserveTheCaughtAssertionWithoutReadingItsMessage()
            throws Exception {
        var assertedFailure = new MessageReadingAssertion();
        var asserted = ConditionProvider.<Payment>asserted(payment -> {
            throw assertedFailure;
        });
        Evaluation<Payment> assertedEvaluation = asserted.runtime()
                .evaluate(new Payment(42));

        var passedFailure = new MessageReadingAssertion();
        var passed = ConditionProvider.<Payment, Long>passed(payment -> {
            throw passedFailure;
        });
        Evaluation<Long> passedEvaluation = passed.evaluate(new Payment(42));

        assertEquals("assertion did not pass", assertedEvaluation.mismatch());
        assertSame(assertedFailure, assertedEvaluation.assertionCause());
        assertEquals(0, assertedFailure.messageReads);
        assertEquals("assertion did not pass", passedEvaluation.mismatch());
        assertSame(passedFailure, passedEvaluation.assertionCause());
        assertEquals(0, passedFailure.messageReads);
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void exceptionsEscapeAssertionAdaptersUnchanged(Exception failure) {
        var asserted = ConditionProvider.<Payment>asserted(payment -> {
            throw failure;
        });
        var passed = ConditionProvider.<Payment, Long>passed(payment -> {
            throw failure;
        });

        assertSame(failure, assertThrows(failure.getClass(),
                () -> asserted.runtime().evaluate(new Payment(42))));
        assertSame(failure, assertThrows(failure.getClass(),
                () -> passed.evaluate(new Payment(42))));
    }

    @Test
    void nonAssertionErrorsEscapeAssertionAdaptersUnchanged() {
        var failure = new LinkageError("error");

        var asserted = ConditionProvider.<Payment>asserted(payment -> {
            throw failure;
        });
        var passed = ConditionProvider.<Payment, Long>passed(payment -> {
            throw failure;
        });

        assertSame(failure, assertThrows(LinkageError.class,
                () -> asserted.runtime().evaluate(new Payment(42))));
        assertSame(failure, assertThrows(LinkageError.class,
                () -> passed.evaluate(new Payment(42))));
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

    private static Stream<Exception> exceptions() {
        return Stream.of(new Exception("checked"),
                new IllegalStateException("runtime"));
    }

    private static Await<Payment> stage(FakeTime time, Payment actual) {
        WaitConfiguration config = defaults()
                .withEvery(Duration.ofNanos(1))
                .withUpTo(Duration.ofNanos(10));
        return new AwaitStage<>((Source<Payment>) () -> actual, config,
                time, time);
    }

    private record Payment(long id) {
    }

    private static final class MessageReadingAssertion extends AssertionError {
        private int messageReads;

        @Override
        public String getMessage() {
            messageReads++;
            return "not completed";
        }
    }
}
