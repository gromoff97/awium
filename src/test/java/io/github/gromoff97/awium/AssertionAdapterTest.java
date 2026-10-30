package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedConsumer;
import io.github.gromoff97.awium.function.CheckedFunction;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.internal.WaitConfiguration.DEFAULT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

class AssertionAdapterTest {

    @Test
    void assertionFailuresRetainTheirCauseWithAndWithoutInterruption() {
        // Exercise ordinary diagnostics first: PIT clears interrupt flags while loading classes.
        for (boolean interrupt : List.of(false, true)) {
            var cause = new AssertionError("payment is not ready");
            var check = asserted((String value) -> {
                if (interrupt) currentThread().interrupt();
                throw cause;
            });
            var time = new FakeTime(0);
            var waiting = await(() -> "pending").usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            var reason = interrupt ? INTERRUPTED : TIMEOUT;
            try {
                var failure = failure(diagnostic(waiting).until(check));
                assertEquals(interrupt, interrupted());
                assertEquals(reason, failure.reason());
                assertSame(cause, failure.cause());

                var thrown = assertFailure(reason, () -> waiting.until(check));
                assertEquals(interrupt, interrupted());
                assertSame(cause, thrown.getCause());
                assertEquals(failure.message(), thrown.getMessage());
            } finally {
                interrupted();
            }
        }
    }

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() {
        var invocations = new int[1];
        var condition = Conditions.<String>condition(
                "payment id", value -> {
                    invocations[0]++;
                    return satisfied();
                });

        AttemptResult<? extends String> evaluation = evaluate(condition, "42");

        assertEquals("42", result(evaluation));
        assertEquals(1, invocations[0]);
        assertEquals("payment id", condition.description());
    }

    @Test
    void namedConditionValidatesItsArguments() {
        assertThatNullPointerException().isThrownBy(() -> condition(null, payment -> satisfied()))
                .withMessageContaining("description");
        assertThatNullPointerException().isThrownBy(() -> condition(null, (CheckedFunction<Object, ConditionResult>) null))
                .withMessageContaining("description");
        assertThatIllegalArgumentException().isThrownBy(() -> condition(" \n ", payment -> satisfied()))
                .withMessageContaining("description");
        assertThatNullPointerException().isThrownBy(() -> condition("payment", (CheckedFunction<Object, ConditionResult>) null))
                .withMessageContaining("evaluation");
        assertThatNullPointerException()
                .isThrownBy(() -> conditionPerWait("payment", (Callable<CheckedFunction<Object, ConditionResult>>) null))
                .withMessageContaining("evaluator factory");
    }

    @Test
    void assertedWithoutResultReturnsTheExactActualAndInvokesItsCallbackOnce() {
        var invocations = new int[1];
        var actual = new String("42");
        var condition = Conditions.<String>asserted(value -> {
            invocations[0]++;
        });

        AttemptResult<? extends String> evaluation = evaluate(condition, actual);

        assertSame(actual, result(evaluation));
        assertEquals(1, invocations[0]);
        assertThat(condition.description()).isNotBlank();
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
                        })));

        assertEquals(1, invocations[0]);
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame},
                failure.getCause().getStackTrace());
    }

    @Test
    void callbackFactoriesRejectNullCallbacks() {
        assertThatNullPointerException().isThrownBy(() -> asserted((CheckedConsumer<String>) null))
                .withMessageContaining("assertion");
    }
}
