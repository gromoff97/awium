package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ScalarAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void booleanTerminalsRetryAndReturnTheSuccessfulValue() {
        var trueCalls = new AtomicInteger();
        var falseCalls = new AtomicInteger();

        var trueValue = await(TestFactories.fast())
                .until(() -> trueCalls.incrementAndGet() >= 2)
                .isTrue();
        var falseValue = tryAwait(TestFactories.fast())
                .until(() -> falseCalls.incrementAndGet() < 2)
                .isFalse();

        assertThat(trueValue).isTrue();
        assertThat(trueCalls).hasValue(2);
        assertThat(falseValue.get()).isFalse();
        assertThat(falseCalls).hasValue(2);
    }

    @Test
    void comparableTerminalsRetryUntilTheirBoundaryMatches() {
        var greaterCalls = new AtomicInteger();
        var greaterOrEqualCalls = new AtomicInteger();
        var lessCalls = new AtomicInteger();
        var lessOrEqualCalls = new AtomicInteger();

        var greater = await(TestFactories.fast())
                .until(() -> greaterCalls.incrementAndGet())
                .isGreaterThan(1);
        var greaterOrEqual = await(TestFactories.fast())
                .until(() -> greaterOrEqualCalls.incrementAndGet() + 1)
                .isGreaterThanOrEqualTo(3);
        var less = await(TestFactories.fast())
                .until(() -> 4 - lessCalls.incrementAndGet())
                .isLessThan(3);
        var lessOrEqual = await(TestFactories.fast())
                .until(() -> 5 - lessOrEqualCalls.incrementAndGet())
                .isLessThanOrEqualTo(3);

        assertThat(greater).isEqualTo(2);
        assertThat(greaterOrEqual).isEqualTo(3);
        assertThat(less).isEqualTo(2);
        assertThat(lessOrEqual).isEqualTo(3);
    }

    @Test
    void stringLambdaExposesAllStringTerminalsAndPreservesInstances() {
        var emptyCalls = new AtomicInteger();
        var nonEmptyCalls = new AtomicInteger();
        var containsCalls = new AtomicInteger();
        var excludesCalls = new AtomicInteger();
        var expectedNonEmpty = new String("ready");
        var expectedContains = new String("alpha beta");
        var expectedExcludes = new String("safe");

        var empty = await(TestFactories.fast())
                .until(() -> emptyCalls.incrementAndGet() < 2 ? "busy" : "")
                .isEmpty();
        var nonEmpty = await(TestFactories.fast())
                .until(() -> nonEmptyCalls.incrementAndGet() < 2 ? null : expectedNonEmpty)
                .as("value must be available")
                .isNotEmpty();
        var contains = await(TestFactories.fast())
                .until(() -> containsCalls.incrementAndGet() < 2 ? "alpha" : expectedContains)
                .contains("alpha", "beta");
        var excludes = await(TestFactories.fast())
                .until(() -> excludesCalls.incrementAndGet() < 2 ? "unsafe" : expectedExcludes)
                .doesNotContain("unsafe", "blocked");

        assertThat(empty).isEmpty();
        assertThat(nonEmpty).isSameAs(expectedNonEmpty);
        assertThat(contains).isSameAs(expectedContains);
        assertThat(excludes).isSameAs(expectedExcludes);
    }

    @Test
    void stringVarargsAreValidatedBeforePolling() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return "ready";
        });

        assertThatNullPointerException()
                .isThrownBy(() -> facade.contains((CharSequence[]) null));
        assertThatNullPointerException()
                .isThrownBy(() -> facade.contains("ready", null));
        assertThatNullPointerException()
                .isThrownBy(() -> facade.doesNotContain((CharSequence[]) null));

        assertThat(calls).hasValue(0);
    }
}
