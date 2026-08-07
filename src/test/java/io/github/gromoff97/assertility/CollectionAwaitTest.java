package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
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

    @Test
    void allIsNonVacuousAndNoneAcceptsAnEmptyCollection() {
        var allSourceCalls = new AtomicInteger();
        var expected = new Payments();
        expected.add(new Payment("p-1", true));
        expected.add(new Payment("p-2", true));
        var empty = new Payments();

        var all = await(TestFactories.fast()).until(() ->
                        allSourceCalls.incrementAndGet() < 2 ? empty : expected)
                .all(Payment::active);
        var none = await(TestFactories.fast()).until(() -> empty)
                .none(Payment::active);
        var emptyAll = tryAwait(TestFactories.fast()).until(() -> empty)
                .all(Payment::active);

        assertThat(all).isSameAs(expected);
        assertThat(none).isSameAs(empty);
        assertThat(allSourceCalls).hasValue(2);
        assertThat(emptyAll.failure().orElseThrow())
                .hasMessageContaining(
                        "expected a non-empty collection whose elements all match");
    }

    @Test
    void quantifierPredicateAndExtractorOverloadsEvaluateEachElementOnce() {
        var payments = List.of(
                new Payment("p-1", true),
                new Payment("p-2", true),
                new Payment("p-3", true));
        var allPredicateCalls = new AtomicInteger();
        var nonePredicateCalls = new AtomicInteger();
        var allExtractorCalls = new AtomicInteger();
        var noneExtractorCalls = new AtomicInteger();

        var allPredicate = await(TestFactories.fast()).until(() -> payments)
                .all("active payments", payment -> {
                    allPredicateCalls.incrementAndGet();
                    return payment.active();
                });
        var nonePredicate = await(TestFactories.fast()).until(() -> payments)
                .none("cancelled payments", payment -> {
                    nonePredicateCalls.incrementAndGet();
                    return payment.id().startsWith("cancelled");
                });
        var allExtractor = await(TestFactories.fast()).until(() -> payments)
                .all(payment -> {
                    allExtractorCalls.incrementAndGet();
                    return payment.active();
                }, true);
        var noneExtractor = await(TestFactories.fast()).until(() -> payments)
                .none(payment -> {
                    noneExtractorCalls.incrementAndGet();
                    return payment.active();
                }, false);

        assertThat(allPredicate).isSameAs(payments);
        assertThat(nonePredicate).isSameAs(payments);
        assertThat(allExtractor).isSameAs(payments);
        assertThat(noneExtractor).isSameAs(payments);
        assertThat(allPredicateCalls).hasValue(3);
        assertThat(nonePredicateCalls).hasValue(3);
        assertThat(allExtractorCalls).hasValue(3);
        assertThat(noneExtractorCalls).hasValue(3);
    }

    @Test
    void contentChecksUseRecursiveEqualityAndPreserveCollectionIdentity() {
        var first = new Entity("e-1", new Detail("one"));
        var duplicateOne = new Entity("e-2", new Detail("same"));
        var duplicateTwo = new Entity("e-2", new Detail("same"));
        var actual = new ArrayList<Entity>();
        actual.add(first);
        actual.add(duplicateOne);
        actual.add(null);
        actual.add(duplicateTwo);
        var expectedExactly = new ArrayList<Entity>();
        expectedExactly.add(new Entity("e-1", new Detail("one")));
        expectedExactly.add(new Entity("e-2", new Detail("same")));
        expectedExactly.add(new Entity("e-2", new Detail("same")));
        expectedExactly.add(null);
        var facade = await(TestFactories.fast()).until(() -> actual);

        var contains = facade.contains(
                new Entity("e-1", new Detail("one")), null);
        var containsAll = facade.containsAll(List.of(
                new Entity("e-2", new Detail("same")),
                new Entity("e-2", new Detail("same"))));
        var excludes = facade.doesNotContain(new Entity("missing", new Detail("none")));
        var exact = facade.containsExactlyInAnyOrder(
                new Entity("e-2", new Detail("same")),
                null,
                new Entity("e-1", new Detail("one")),
                new Entity("e-2", new Detail("same")));
        var exactElements = facade.containsExactlyInAnyOrderElementsOf(expectedExactly);

        assertThat(contains).isSameAs(actual);
        assertThat(containsAll).isSameAs(actual);
        assertThat(excludes).isSameAs(actual);
        assertThat(exact).isSameAs(actual);
        assertThat(exactElements).isSameAs(actual);
    }

    @Test
    void recursiveContentComparisonUsesStrictTypes() {
        var actual = List.of(new Box(10));

        var result = tryAwait(TestFactories.fast()).until(() -> actual)
                .contains(new Box(10L));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void orderedContentIsAvailableForListsAndDeques() {
        var first = new Entity("e-1", new Detail("one"));
        var second = new Entity("e-2", new Detail("two"));
        var list = new ArrayList<>(List.of(first, second));
        var deque = new ArrayDeque<>(List.of(first, second));

        var orderedList = await(TestFactories.fast()).until(() -> list)
                .containsExactly(
                        new Entity("e-1", new Detail("one")),
                        new Entity("e-2", new Detail("two")));
        var orderedDeque = await(TestFactories.fast()).until(() -> deque)
                .containsExactlyElementsOf(List.of(
                        new Entity("e-1", new Detail("one")),
                        new Entity("e-2", new Detail("two"))));
        var wrongOrder = tryAwait(TestFactories.fast()).until(() -> list)
                .containsExactly(
                        new Entity("e-2", new Detail("two")),
                        new Entity("e-1", new Detail("one")));

        assertThat(orderedList).isSameAs(list);
        assertThat(orderedDeque).isSameAs(deque);
        assertThat(wrongOrder.isSuccess()).isFalse();
    }

    @Test
    void contentContainersAndVarargsAreValidatedBeforePolling() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return List.of("ready");
        });

        assertThatThrownBy(() -> facade.contains((String[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> facade.doesNotContain((String[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> facade.containsAll(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> facade.containsExactlyInAnyOrderElementsOf(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(calls).hasValue(0);
    }

    private record Payment(String id, boolean active) {
    }

    private static final class Payments extends ArrayList<Payment> {
    }

    private static final class Entity {
        private final String id;
        private final Detail detail;

        private Entity(String id, Detail detail) {
            this.id = id;
            this.detail = detail;
        }
    }

    private record Detail(String value) {
    }

    private record Box(Object value) {
    }
}
