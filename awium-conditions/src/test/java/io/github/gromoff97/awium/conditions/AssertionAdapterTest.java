package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.function.CheckedConsumer;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.result;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.thrown;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class AssertionAdapterTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assertionFailuresBecomeMismatchesAndKeepTheOriginalCause(boolean interrupt) throws Throwable {
        var cause = new AssertionError("payment is not ready");
        var check = asserted((String value) -> {
            if (interrupt) currentThread().interrupt();
            throw cause;
        });
        var mismatch = assertInstanceOf(ConditionResult.Unsatisfied.class, check.newAttempt().probe("pending"));
        assertSame(cause, mismatch.cause());
        assertEquals(interrupt, interrupted());
    }

    @Test
    void namedConditionDelegatesOnceAndSuppliesItsDescription() {
        var invocations = new int[1];
        var condition = Conditions.condition(
                "payment id", (String value) -> {
                    invocations[0]++;
                    return satisfied(value);
                });

        ConditionResult<? extends String> evaluation = evaluate(condition, "42");

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

        ConditionResult<? extends String> evaluation = evaluate(condition, actual);

        assertSame(actual, result(evaluation));
        assertEquals(1, invocations[0]);
        assertThat(condition.description()).isNotBlank();
    }

    @Test
    void customConditionPreservesThrownAssertionsAndTheirStackTrace() {
        var assertion = new AssertionError("selection failed");
        var frame = new StackTraceElement("PaymentSelector", "select", "PaymentSelector.java", 42);
        assertion.setStackTrace(new StackTraceElement[]{frame});
        int[] invocations = {0};
        var check = condition("checked value", (String value) -> {
            invocations[0]++;
            throw assertion;
        });
        assertSame(assertion, thrown(check, "42"));
        assertEquals(1, invocations[0]);
        assertArrayEquals(new StackTraceElement[]{frame}, assertion.getStackTrace());
    }
}
