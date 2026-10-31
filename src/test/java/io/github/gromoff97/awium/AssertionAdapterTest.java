package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedConsumer;
import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.internal.WaitConfiguration.DEFAULT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

@Execution(SAME_THREAD)
class AssertionAdapterTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @MethodSource("assertionInterruptions")
    void assertionFailuresRetainTheirCauseInBothTerminals(Runnable beforeFailure, Reason reason, boolean expectedInterrupt) {
        var cause = new AssertionError("payment is not ready");
        var check = asserted((String value) -> {
            beforeFailure.run();
            throw cause;
        });
        var time = new FakeTime(0);
        var waiting = await(() -> "pending").usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var failure = failure(diagnostic(waiting).until(check));
        assertEquals(expectedInterrupt, interrupted());
        assertEquals(reason, failure.reason());
        assertSame(cause, failure.cause());

        var thrown = assertFailure(reason, () -> waiting.until(check));
        assertEquals(expectedInterrupt, interrupted());
        assertSame(cause, thrown.getCause());
        assertEquals(failure.message(), thrown.getMessage());
    }

    private static List<Arguments> assertionInterruptions() {
        // Exercise ordinary diagnostics first: PIT clears interrupt flags while loading classes.
        return List.of(argumentSet("ordinary assertion", (Runnable) () -> {}, TIMEOUT, false),
                argumentSet("interrupted assertion", (Runnable) () -> currentThread().interrupt(), INTERRUPTED, true));
    }

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() {
        var invocations = new int[1];
        var condition = Conditions.condition(
                "payment id", (String value) -> {
                    invocations[0]++;
                    return satisfied(value);
                });

        AttemptResult<? extends String> evaluation = evaluate(condition, "42");

        assertEquals("42", result(evaluation));
        assertEquals(1, invocations[0]);
        assertEquals("payment id", condition.description());
    }

    @TestFactory
    List<DynamicTest> customConditionFactoriesRejectInvalidArguments() {
        return List.of(
                dynamicTest("null description", () -> assertThatNullPointerException()
                        .isThrownBy(() -> condition(null, payment -> satisfied(payment))).withMessageContaining("description")),
                dynamicTest("null description and callback", () -> assertThatNullPointerException()
                        .isThrownBy(() -> condition(null, null)).withMessageContaining("description")),
                dynamicTest("blank description", () -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> condition(" \n ", payment -> satisfied(payment))).withMessageContaining("description")),
                dynamicTest("null evaluation", () -> assertThatNullPointerException()
                        .isThrownBy(() -> condition("payment", null)).withMessageContaining("evaluation")),
                dynamicTest("null evaluator factory", () -> assertThatNullPointerException()
                        .isThrownBy(() -> conditionPerWait("payment", null))
                        .withMessageContaining("evaluator factory")),
                dynamicTest("null assertion", () -> assertThatNullPointerException()
                        .isThrownBy(() -> asserted((CheckedConsumer<String>) null)).withMessageContaining("assertion")));
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
                DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(10)), time, time).until(Conditions.condition("checked value", (String value) -> {
                            invocations[0]++;
                            throw assertion;
                        })));

        assertEquals(1, invocations[0]);
        assertSame(assertion, failure.getCause());
        assertArrayEquals(new StackTraceElement[]{frame},
                failure.getCause().getStackTrace());
    }


}
