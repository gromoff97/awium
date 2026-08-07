package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void stateAndSizeTerminalsPreserveTheDeclaredCollectionInstance() {
        var payment = new Payment("p-1", true);
        var set = new LinkedHashSet<>(Set.of(payment));
        var custom = new Payments();
        custom.add(payment);
        var empty = new Payments();

        var emptyResult = await(TestFactories.fast()).until(() -> empty).isEmpty();
        var nonEmptyResult = await(TestFactories.fast()).until(() -> set).isNotEmpty();
        var exact = await(TestFactories.fast()).until(() -> custom).hasSize(1);
        var greater = await(TestFactories.fast()).until(() -> custom).hasSizeGreaterThan(0);
        var greaterOrEqual = await(TestFactories.fast()).until(() -> custom)
                .hasSizeGreaterThanOrEqualTo(1);
        var less = await(TestFactories.fast()).until(() -> custom).hasSizeLessThan(2);
        var lessOrEqual = await(TestFactories.fast()).until(() -> custom)
                .hasSizeLessThanOrEqualTo(1);

        assertThat(emptyResult).isSameAs(empty);
        assertThat(nonEmptyResult).isSameAs(set);
        assertThat(exact).isSameAs(custom);
        assertThat(greater).isSameAs(custom);
        assertThat(greaterOrEqual).isSameAs(custom);
        assertThat(less).isSameAs(custom);
        assertThat(lessOrEqual).isSameAs(custom);
    }

    @Test
    void singleWaitsForExactCardinalityAndReturnsTheElementInstance() {
        var calls = new AtomicInteger();
        var expected = new Payment("p-1", true);
        var other = new Payment("p-2", false);

        var actual = await(TestFactories.fast()).until(() -> switch (calls.incrementAndGet()) {
            case 1 -> List.<Payment>of();
            case 2 -> List.of(expected, other);
            default -> List.of(expected);
        }).single();

        assertThat(actual).isSameAs(expected);
        assertThat(calls).hasValue(3);
    }

    @Test
    void predicateAndExtractorSelectorsEvaluateEveryElementOnce() {
        var first = new Payment("p-1", false);
        var expected = new Payment("p-2", true);
        var third = new Payment("p-3", false);
        var payments = List.of(first, expected, third);
        var predicateCalls = new AtomicInteger();
        var extractorCalls = new AtomicInteger();

        var byPredicate = await(TestFactories.fast()).until(() -> payments)
                .single("active payment", payment -> {
                    predicateCalls.incrementAndGet();
                    return payment.active();
                });
        var byExtractor = await(TestFactories.fast()).until(() -> payments)
                .single(payment -> {
                    extractorCalls.incrementAndGet();
                    return payment.id();
                }, "p-2");

        assertThat(byPredicate).isSameAs(expected);
        assertThat(byExtractor).isSameAs(expected);
        assertThat(predicateCalls).hasValue(3);
        assertThat(extractorCalls).hasValue(3);
    }

    @Test
    void exactlyWaitsThroughUnderAndOverCardinality() {
        var calls = new AtomicInteger();
        var first = new Payment("p-1", true);
        var second = new Payment("p-2", true);
        var third = new Payment("p-3", true);

        var result = await(TestFactories.fast()).until(() -> switch (calls.incrementAndGet()) {
            case 1 -> List.of(first);
            case 2 -> List.of(first, second, third);
            default -> List.of(first, second);
        }).exactly(2, Payment::active);

        assertThat(result).containsExactly(first, second);
        assertThat(result.get(0)).isSameAs(first);
        assertThat(result.get(1)).isSameAs(second);
        assertThat(calls).hasValue(3);
    }

    @Test
    void exactlyReturnsAnUnmodifiableOrderedSnapshotThatAllowsNulls() {
        var first = new Payment("p-1", true);
        var second = new Payment("p-2", true);
        var source = new ArrayList<Payment>();
        source.add(first);
        source.add(null);
        source.add(second);

        var result = await(TestFactories.fast()).until(() -> source)
                .exactly(3, payment -> true);
        source.clear();

        assertThat(result).containsExactly(first, null, second);
        assertThat(result.get(0)).isSameAs(first);
        assertThat(result.get(2)).isSameAs(second);
        assertThatThrownBy(() -> result.add(new Payment("p-3", false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anyReturnsOnlyOneOfTheMatchingElements() {
        var excluded = new Payment("p-0", false);
        var firstMatch = new Payment("p-1", true);
        var secondMatch = new Payment("p-2", true);
        var payments = List.of(excluded, firstMatch, secondMatch);
        var facade = await(TestFactories.fast()).until(() -> payments);
        var selected = new ArrayList<Payment>();

        for (var iteration = 0; iteration < 50; iteration++) {
            selected.add(facade.any(Payment::active));
        }

        assertThat(selected).allMatch(value -> value == firstMatch || value == secondMatch);
        assertThat(selected).doesNotContain(excluded);
    }

    @Test
    void invalidSizesAndExactlyCountsFailBeforePolling() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return List.of(new Payment("p-1", true));
        });

        assertThatIllegalArgumentException().isThrownBy(() -> facade.hasSize(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.hasSizeGreaterThan(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.hasSizeGreaterThanOrEqualTo(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.hasSizeLessThan(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.hasSizeLessThanOrEqualTo(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.exactly(0, Payment::active));
        assertThatIllegalArgumentException().isThrownBy(() -> facade.exactly(1, Payment::active));

        assertThat(calls).hasValue(0);
    }

    @Test
    void predicateSelectorDiagnosticsAreBounded() {
        var payments = new ArrayList<Payment>();
        for (var index = 0; index < 25; index++) {
            payments.add(new Payment("p-" + index, false));
        }

        var result = tryAwait(TestFactories.fast()).until(() -> payments)
                .single("active payment", Payment::active);
        var message = result.failure().orElseThrow().getMessage();

        assertThat(message)
                .contains("active payment")
                .contains("source size: 25")
                .contains("expected matches: 1")
                .contains("actual matches: 0")
                .contains("omitted elements: 15")
                .doesNotContain("p-20");
    }

    @Test
    void extractorSelectorDiagnosticsKeepOnlyThreeFullDiffs() {
        var payments = new ArrayList<Payment>();
        for (var index = 0; index < 10; index++) {
            payments.add(new Payment("p-" + index, false));
        }

        var result = tryAwait(TestFactories.fast()).until(() -> payments)
                .single(Payment::id, "missing");
        var message = result.failure().orElseThrow().getMessage();

        assertThat(message)
                .contains("candidate comparison failures")
                .contains("omitted candidate comparisons: 7");
    }

    @Test
    void selectorPredicateAssertionErrorPropagatesImmediately() {
        var failure = new AssertionError("predicate defect");

        assertThatThrownBy(() -> await(TestFactories.fast())
                .until(() -> List.of(new Payment("p-1", true)))
                .any(payment -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }

    private record Payment(String id, boolean active) {
    }

    private static final class Payments extends ArrayList<Payment> {
    }
}
