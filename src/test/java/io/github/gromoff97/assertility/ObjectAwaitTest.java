package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void returnsOriginalObjectFromTheSameSuccessfulObservation() {
        var calls = new AtomicInteger();
        var expected = new Payment("p-1", "COMPLETED");

        var actual = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return expected;
        }).returns("COMPLETED", Payment::status);

        assertThat(actual).isSameAs(expected);
        assertThat(calls).hasValue(1);
    }

    @Test
    void nullIsReadyOnlyForNullTerminals() {
        AwaitSources.StringSource nullString = () -> null;

        var nullValue = await(TestFactories.fast()).until(nullString).isNull();
        var equalNull = await(TestFactories.fast()).until(nullString).isEqualTo(null);
        var unequalNonNull = tryAwait(TestFactories.fast())
                .until(nullString)
                .isNotEqualTo("ready");

        assertThat(nullValue).isNull();
        assertThat(equalNull).isNull();
        assertThat(unequalNonNull.isSuccess()).isFalse();
    }

    @Test
    void equalityUsesStrictRecursiveComparison() {
        var actual = new Box(new Amount("10"));
        var equal = await(TestFactories.fast()).until(() -> actual)
                .isEqualTo(new Box(new Amount("10")));
        var typeMismatch = tryAwait(TestFactories.fast()).until(() -> new Box(10))
                .isEqualTo(new Box(10L));

        assertThat(equal).isSameAs(actual);
        assertThat(typeMismatch.isSuccess()).isFalse();
    }

    @Test
    void describedPredicateAppearsInFailure() {
        var result = tryAwait(TestFactories.fast()).until(() -> new Payment("p-1", "NEW"))
                .matches("payment has a final status", payment -> "COMPLETED".equals(payment.status()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure().orElseThrow())
                .hasMessageContaining("payment has a final status")
                .hasMessageContaining("matches");
    }

    @Test
    void satisfiesRetriesAssertionFailures() {
        var calls = new AtomicInteger();

        var actual = await(TestFactories.fast()).until(calls::incrementAndGet)
                .satisfies(value -> assertThat(value).isGreaterThanOrEqualTo(3));

        assertThat(actual).isEqualTo(3);
        assertThat(calls).hasValue(3);
    }

    @Test
    void failedResultRethrowsItsStoredFailure() {
        var result = tryAwait(TestFactories.fast()).until(() -> "NEW")
                .isEqualTo("COMPLETED");
        var failure = result.failure().orElseThrow();

        assertThat(result.isSuccess()).isFalse();
        assertThatThrownBy(result::get).isSameAs(failure);
    }

    @Test
    void facadeCanStartIndependentTerminalExecutions() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(calls::incrementAndGet);

        var first = facade.matches(value -> value >= 1);
        var second = facade.matches(value -> value >= 2);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(calls).hasValue(2);
    }

    @Test
    void predicateAndExtractorAssertionErrorsPropagateImmediately() {
        var predicateError = new AssertionError("predicate defect");
        var extractorError = new AssertionError("extractor defect");

        assertThatThrownBy(() -> await(TestFactories.fast()).until(() -> "ready")
                .matches(value -> {
                    throw predicateError;
                })).isSameAs(predicateError);
        assertThatThrownBy(() -> await(TestFactories.fast()).until(() -> "ready")
                .returns("READY", value -> {
                    throw extractorError;
                })).isSameAs(extractorError);
    }

    private record Payment(String id, String status) {
    }

    private record Amount(String value) {
    }

    private record Box(Object value) {
    }
}
