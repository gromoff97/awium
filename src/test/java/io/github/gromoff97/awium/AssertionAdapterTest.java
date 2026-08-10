package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.internal.diagnostic.*;

import io.github.gromoff97.awium.engine.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AssertionAdapterTest {

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() throws Exception {
        var invocations = new int[1];
        var condition = ConditionProvider.<Payment, Long>condition(
                "payment id", payment -> {
                    invocations[0]++;
                    return Evaluation.satisfied(payment.id());
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
                        () -> ConditionProvider.condition(null,
                                payment -> Evaluation.satisfied(payment)))
                        .getMessage());
        assertEquals("description must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> ConditionProvider.condition(" \n ",
                                payment -> Evaluation.satisfied(payment)))
                        .getMessage());
        assertEquals("evaluation must not be null",
                assertThrows(NullPointerException.class,
                        () -> ConditionProvider.condition("payment", null))
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

    @Test
    void checkedExceptionsEscapeAssertionAdaptersUnchanged() {
        var failure = new Exception("checked");

        var asserted = ConditionProvider.<Payment>asserted(payment -> {
            throw failure;
        });
        var passed = ConditionProvider.<Payment, Long>passed(payment -> {
            throw failure;
        });

        assertSame(failure, assertThrows(Exception.class,
                () -> asserted.runtime().evaluate(new Payment(42))));
        assertSame(failure, assertThrows(Exception.class,
                () -> passed.evaluate(new Payment(42))));
    }

    @Test
    void runtimeExceptionsEscapeAssertionAdaptersUnchanged() {
        var failure = new IllegalStateException("runtime");

        var asserted = ConditionProvider.<Payment>asserted(payment -> {
            throw failure;
        });
        var passed = ConditionProvider.<Payment, Long>passed(payment -> {
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> asserted.runtime().evaluate(new Payment(42))));
        assertSame(failure, assertThrows(IllegalStateException.class,
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
                        () -> ConditionProvider.asserted(null)).getMessage());
        assertEquals("assertion must not be null",
                assertThrows(NullPointerException.class,
                        () -> ConditionProvider.passed(null)).getMessage());
    }

    @Test
    void conditionProviderIsAnUninstantiableStaticNamespace()
            throws ReflectiveOperationException {
        assertTrue(isPublic(ConditionProvider.class.getModifiers()));
        assertTrue(isFinal(ConditionProvider.class.getModifiers()));
        Constructor<ConditionProvider> constructor =
                ConditionProvider.class.getDeclaredConstructor();
        assertTrue(isPrivate(constructor.getModifiers()));
        assertEquals(1, ConditionProvider.class.getDeclaredConstructors().length);
        assertTrue(Arrays.stream(ConditionProvider.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .allMatch(method -> isPublic(method.getModifiers())
                        && isStatic(method.getModifiers())));
    }

    private static Await.Until<Payment> stage(FakeTime time, Payment actual) {
        WaitConfiguration config = WaitConfiguration.defaults()
                .withEvery(Duration.ofNanos(1))
                .withUpTo(Duration.ofNanos(10));
        return new AwaitStage<>((Source<Payment>) () -> actual, config,
                time, time, new FailureFactory());
    }

    private record Payment(long id) {
    }

    private static final class MessageReadingAssertion extends AssertionError {
        private static final long serialVersionUID = 1L;
        private int messageReads;

        @Override
        public String getMessage() {
            messageReads++;
            return "not completed";
        }
    }
}
