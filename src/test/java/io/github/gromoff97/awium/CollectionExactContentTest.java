package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.ConditionEvaluation;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.ConditionEvaluation.Satisfied;
import io.github.gromoff97.awium.internal.ConditionEvaluation.Unsatisfied;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class CollectionExactContentTest {

    @Test
    void positiveAndNegativeExactConditionsAreComplements() {
        for (Pair pair : pairs()) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void orderedAndAnyOrderFormsKeepTheirDistinctSemantics() {
        assertStatus(containsExactly(List.of("a", "b")), List.of("b", "a"),
                Unsatisfied.class);
        assertStatus(containsExactlyInAnyOrder(List.of("a", "b")),
                List.of("b", "a"), Satisfied.class);
        assertStatus(containsExactly(List.of()), List.of(), Satisfied.class);
        assertStatus(containsExactly(List.of("a", "a")), List.of("a", "a"),
                Satisfied.class);
    }

    @Test
    void exactContentRequiresEqualSizesWithoutStartingTraversal() {
        for (var elements : List.of(List.<String>of(), List.of("a"), List.of("a", "b", "c"))) {
            for (boolean negative : List.of(false, true)) {
                for (boolean anyOrder : List.of(false, true)) {
                    var actual = new ArrayList<>(elements) {
                        @Override
                        public Iterator<String> iterator() {
                            throw new AssertionError("different sizes must not start traversal");
                        }
                    };
                    PreservingCondition<? super ArrayList<String>> condition = anyOrder
                            ? negative ? doesNotContainExactlyInAnyOrder(List.of("a", "b")) : containsExactlyInAnyOrder(List.of("a", "b"))
                            : negative ? doesNotContainExactly(List.of("a", "b")) : containsExactly(List.of("a", "b"));
                    var evaluation = evaluate(condition, actual);
                    assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                    if (negative) assertSame(actual, result(evaluation));
                }
            }
        }
    }

    @Test
    void onlyElementsRejectMissingAndExtraValuesButIgnoreDuplicates() {
        var condition = containsOnly(List.of("a", "b", "a"));
        assertStatus(condition, List.of(), Unsatisfied.class);
        assertStatus(condition, List.of("a"), Unsatisfied.class);
        assertStatus(condition, List.of("b"), Unsatisfied.class);
        assertStatus(condition, List.of("a", "b", "c"), Unsatisfied.class);
        assertStatus(condition, List.of("b", "a", "b"), Satisfied.class);
        assertStatus(containsOnly(List.of()), List.of(), Satisfied.class);
        assertStatus(containsOnly(List.of()), List.of("a"), Unsatisfied.class);
    }

    @Test
    void exactComparisonRejectsChangesBetweenSizeAndTraversal() {
        for (boolean shrink : List.of(false, true)) {
            for (boolean negative : List.of(false, true)) {
                for (boolean anyOrder : List.of(false, true)) {
                    var actual = new ArrayList<>(List.of("a", "b")) {
                        private boolean changed;

                        @Override
                        public Iterator<String> iterator() {
                            if (!changed) {
                                changed = true;
                                if (shrink) removeLast();
                                else add("c");
                            }
                            return super.iterator();
                        }
                    };
                    PreservingCondition<? super ArrayList<String>> condition = anyOrder
                            ? negative ? doesNotContainExactlyInAnyOrder(List.of("a", "b")) : containsExactlyInAnyOrder(List.of("a", "b"))
                            : negative ? doesNotContainExactly(List.of("a", "b")) : containsExactly(List.of("a", "b"));
                    var evaluation = evaluate(condition, actual);
                    if (negative) {
                        assertEquals(Satisfied.class, evaluation.getClass());
                        assertSame(actual, result(evaluation));
                    } else {
                        assertEquals(Unsatisfied.class, evaluation.getClass());
                    }
                }
            }
        }
    }

    @Test
    void exactEqualityIsActualFirstArrayAwareAndDoesNotHash() {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);
        assertStatus(containsExactlyInAnyOrder(asList(expected)), List.of(actual),
                Satisfied.class);
        assertEquals(1, actual.equalsCalls);

        String nil = null;
        assertStatus(containsExactly(asList(nil)), asList((String) null), Satisfied.class);
        assertStatus(containsExactly(java.util.Collections.singletonList(new int[] {1, 2})),
                List.<Object>of(new int[] {1, 2}), Satisfied.class);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnly(asList(onlyExpected)), List.of(onlyActual), Satisfied.class);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void anyOrderMatchingIgnoresPermutationButRetainsMultiplicity() {
        var actual = new ArrayList<>(List.of("a", "b", "a"));
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));

        for (var expected : List.of(List.of("a", "b", "a"), List.of("b", "a", "a"), List.of("a", "a", "b"))) {
            assertSame(actual, waiting.until(containsExactlyInAnyOrder(expected)));
        }
        var differentCounts = List.of("a", "b", "b");
        var failed = assertInstanceOf(AwaitResult.Failed.class,
                waiting.tryUntil(containsExactlyInAnyOrder(differentCounts)));
        assertEquals(TIMEOUT, failed.failure().reason());
        assertSame(actual, waiting.until(doesNotContainExactlyInAnyOrder(differentCounts)));
    }

    @Test
    void negativeExactConditionsDoNotHideTraversalOrEqualityFailures() {
        var pollingTime = new FakeTime(0);
        var iteratorCause = new IllegalStateException("iterator failed");
        var equalityCause = new IllegalStateException("equals failed");
        var brokenIterator = new ProbeList<>(List.of("a"), iteratorCause);
        var brokenEquality = new ProbeList<>(
                List.of(new ThrowingEquals(equalityCause)), null);
        java.util.concurrent.Callable<ProbeList<String>> iteratorSource = () -> brokenIterator;
        java.util.concurrent.Callable<ProbeList<ThrowingEquals>> equalitySource = () -> brokenEquality;

        assertSame(iteratorCause, assertFailure(CONDITION_FAILED,
                () -> await(iteratorSource::call).usingTime(pollingTime, pollingTime).until(doesNotContainExactly(List.of("a")))).getCause());
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> await(equalitySource::call).usingTime(pollingTime, pollingTime).until(doesNotContainExactly(asList(new ThrowingEquals(null))))).getCause());
    }

    @Test
    void nullActualIsUnsatisfiedAndNullAggregateIsRejected() {
        ConditionEvaluation<?> evaluation = evaluate(
                containsExactly(List.of("a")), null);
        assertEquals(Unsatisfied.class, evaluation.getClass());
        assertFalse(mismatch(evaluation).isBlank());
        assertFalse(assertThrows(NullPointerException.class,
                () -> containsExactly(null)).getMessage().isBlank());
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = new ProbeList<>(List.of("a"), null);
        FakeTime time = new FakeTime(0);

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(containsExactly(List.of("b"))));

        assertEquals(1, actual.iteratorCalls);
        assertTrue(failure.getMessage().contains("Expected: [b]"));
    }

    private static void assertPair(Pair pair, List<String> actual,
            boolean positiveSatisfied) {
        ConditionEvaluation<?> positive = evaluate(pair.positive(), actual);
        ConditionEvaluation<?> negative = evaluate(pair.negative(), actual);
        assertAll(pair.name(),
                () -> assertSame(actual, result(positiveSatisfied ? positive : negative)),
                () -> assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank()));
    }

    private static <E> void assertStatus(
            PreservingCondition<? super List<E>> condition,
            List<? extends E> elements, Class<?> status) {
        List<E> actual = new ArrayList<>(elements);
        assertEquals(status, evaluate(condition, actual).getClass());
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("ordered collection",
                        containsExactly(List.of("a", "a")),
                        doesNotContainExactly(List.of("a", "a")),
                        List.of("a", "a"), List.of("a", "b")),
                new Pair("any-order collection",
                        containsExactlyInAnyOrder(List.of("a", "b")),
                        doesNotContainExactlyInAnyOrder(
                                List.of("a", "b")),
                        List.of("b", "a"), List.of("a", "a")));
    }

    private record Pair(String name,
            PreservingCondition<? super List<String>> positive,
            PreservingCondition<? super List<String>> negative,
            List<String> matchingActual, List<String> mismatchingActual) {}

    private static final class ProbeList<E> extends AbstractList<E> {
        private final List<E> elements;
        private final RuntimeException iteratorFailure;
        private int iteratorCalls;

        private ProbeList(List<E> elements,
                RuntimeException iteratorFailure) {
            this.elements = elements;
            this.iteratorFailure = iteratorFailure;
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public E get(int index) {
            throw new AssertionError("get must not be called");
        }

        @Override
        public Iterator<E> iterator() {
            iteratorCalls++;
            if (iteratorFailure != null) {
                throw iteratorFailure;
            }
            return elements.iterator();
        }

        @Override
        public String toString() {
            return "probe list";
        }
    }
}
