package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void emptyReturnsTheExactOptionalObservation() {
        Optional<Payment> expected = Optional.empty();

        var actual = await(TestFactories.fast()).until(() -> expected).isEmpty();

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void positiveTerminalsReturnTheContainedInstance() {
        var presentCalls = new AtomicInteger();
        var predicateSourceCalls = new AtomicInteger();
        var predicateCalls = new AtomicInteger();
        var extractorSourceCalls = new AtomicInteger();
        var extractorCalls = new AtomicInteger();
        var payment = new Payment("p-1", new Status("COMPLETED"));

        var present = await(TestFactories.fast()).until(() ->
                        presentCalls.incrementAndGet() < 2 ? Optional.empty() : Optional.of(payment))
                .isPresent();
        var matching = await(TestFactories.fast()).until(() ->
                        predicateSourceCalls.incrementAndGet() < 2
                                ? Optional.empty() : Optional.of(payment))
                .isPresent(value -> predicateCalls.incrementAndGet() == 1);
        var contained = await(TestFactories.fast()).until(() -> Optional.of(payment))
                .contains(new Payment("p-1", new Status("COMPLETED")));
        var extracted = await(TestFactories.fast()).until(() ->
                        extractorSourceCalls.incrementAndGet() < 2
                                ? Optional.empty() : Optional.of(payment))
                .contains(new Status("COMPLETED"), value -> {
                    extractorCalls.incrementAndGet();
                    return value.status();
                });

        assertThat(present).isSameAs(payment);
        assertThat(matching).isSameAs(payment);
        assertThat(contained).isSameAs(payment);
        assertThat(extracted).isSameAs(payment);
        assertThat(predicateCalls).hasValue(1);
        assertThat(extractorCalls).hasValue(1);
    }

    @Test
    void emptyObservationsDoNotInvokePredicateOrExtractor() {
        var sourceCalls = new AtomicInteger();
        var predicateCalls = new AtomicInteger();
        var extractorSourceCalls = new AtomicInteger();
        var extractorCalls = new AtomicInteger();
        var payment = new Payment("p-1", new Status("COMPLETED"));

        var selected = await(TestFactories.fast()).until(() ->
                        sourceCalls.incrementAndGet() < 3 ? Optional.empty() : Optional.of(payment))
                .isPresent(value -> {
                    predicateCalls.incrementAndGet();
                    return true;
                });
        var extracted = await(TestFactories.fast()).until(() ->
                        extractorSourceCalls.incrementAndGet() < 2
                                ? Optional.empty() : Optional.of(payment))
                .contains("p-1", value -> {
                    extractorCalls.incrementAndGet();
                    return value.id();
                });

        assertThat(selected).isSameAs(payment);
        assertThat(extracted).isSameAs(payment);
        assertThat(sourceCalls).hasValue(3);
        assertThat(predicateCalls).hasValue(1);
        assertThat(extractorCalls).hasValue(1);
    }

    @Test
    void commonMatchesReceivesAndReturnsTheOptionalItself() {
        var expected = Optional.of(new Payment("p-1", new Status("COMPLETED")));

        var actual = await(TestFactories.fast()).until(() -> expected)
                .matches(Optional::isPresent);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void extractorEqualityUsesStrictRecursiveComparison() {
        var payment = new Payment("p-1", 10);

        var result = tryAwait(TestFactories.fast()).until(() -> Optional.of(payment))
                .contains(10L, Payment::status);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void predicateAssertionErrorPropagatesImmediately() {
        var failure = new AssertionError("predicate defect");

        assertThatThrownBy(() -> await(TestFactories.fast())
                .until(() -> Optional.of(new Payment("p-1", "COMPLETED")))
                .isPresent("completed payment", value -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }

    private record Payment(String id, Object status) {
    }

    private record Status(String name) {
    }
}
