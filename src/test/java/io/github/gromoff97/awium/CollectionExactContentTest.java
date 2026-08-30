package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ExpectedValue;
import static io.github.gromoff97.awium.ProbeContainers.GreedyValue;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.*;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionExactContentTest {

    @Test
    void positiveAndNegativeExactConditionsAreComplements()
            throws Exception {
        for (Pair pair : pairs()) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void orderedAndAnyOrderFormsKeepTheirDistinctSemantics()
            throws Exception {
        assertStatus(containsExactly("a", "b"), List.of("b", "a"),
                UNSATISFIED);
        assertStatus(containsExactlyInAnyOrder("a", "b"),
                List.of("b", "a"), SATISFIED);
        assertStatus(containsExactly(), List.of(), SATISFIED);
        assertStatus(containsExactly("a", "a"), List.of("a", "a"),
                SATISFIED);
    }

    @Test
    void exactEqualityIsActualFirstArrayAwareAndDoesNotHash()
            throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);
        assertStatus(containsExactlyInAnyOrder(expected), List.of(actual),
                SATISFIED);
        assertEquals(1, actual.equalsCalls);

        String nil = null;
        assertStatus(containsExactly(nil), asList((String) null), SATISFIED);
        assertStatus(containsExactly(new int[] {1, 2}),
                List.<Object>of(new int[] {1, 2}), SATISFIED);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnly(onlyExpected), List.of(onlyActual), SATISFIED);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void anyOrderMatchingRemainsGreedyInEncounterOrder() throws Exception {
        GreedyValue first = new GreedyValue(Set.of("x", "y"));
        GreedyValue second = new GreedyValue(Set.of("x"));
        ExpectedValue x = new ExpectedValue("x");
        ExpectedValue y = new ExpectedValue("y");

        assertStatus(containsExactlyInAnyOrder(x, y),
                List.of(first, second), UNSATISFIED);
        assertStatus(containsExactlyInAnyOrder(y, x),
                List.of(first, second), SATISFIED);
    }

    @Test
    void exactConditionsObserveUserOwnedExpectedValuesAtEvaluationTime()
            throws Exception {
        String[] array = {"before"};
        PreservingCondition<? super List<String>> arrayCondition =
                containsExactly(array);
        array[0] = "after";
        assertStatus(arrayCondition, List.of("after"), SATISFIED);

        List<String> expected = new ArrayList<>(List.of("before"));
        PreservingCondition<? super List<String>> collectionCondition =
                containsExactlyElementsOf(expected);
        expected.set(0, "after");
        assertStatus(collectionCondition, List.of("after"), SATISFIED);
    }

    @Test
    void negativeExactConditionsDoNotHideTraversalOrEqualityFailures() {
        var iteratorCause = new IllegalStateException("iterator failed");
        var equalityCause = new IllegalStateException("equals failed");
        var brokenIterator = new ProbeList<>(List.of("a"), iteratorCause);
        var brokenEquality = new ProbeList<>(
                List.of(new ThrowingEquals(equalityCause)), null);
        CollectionSource<ProbeList<String>> iteratorSource = () -> brokenIterator;
        CollectionSource<ProbeList<ThrowingEquals>> equalitySource = () -> brokenEquality;

        assertSame(iteratorCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(iteratorSource).until(doesNotContainExactly("a"))).getCause());
        assertSame(equalityCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(equalitySource).until(doesNotContainExactly(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void nullActualIsUnsatisfiedAndNullAggregateIsRejected() throws Exception {
        ConditionEvaluation<?> evaluation = evaluate(
                containsExactlyElementsOf(List.of("a")), null);
        assertEquals(UNSATISFIED, evaluation.status());
        assertFalse(mismatch(evaluation).isBlank());
        assertTrue(!assertThrows(NullPointerException.class,
                () -> containsExactly((Object[]) null)).getMessage().isBlank());
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = new ProbeList<>(List.of("a"), null);
        FakeTime time = new FakeTime(0);

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait((Source<ProbeList<String>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(containsExactly("b").because("business reason")));

        assertEquals(1, actual.iteratorCalls);
        assertTrue(failure.getMessage().contains("Expected: [b]"));
    }

    private static void assertPair(Pair pair, List<String> actual,
            boolean positiveSatisfied) throws Exception {
        ConditionEvaluation<?> positive = evaluate(pair.positive(), actual);
        ConditionEvaluation<?> negative = evaluate(pair.negative(), actual);
        assertEquals(positiveSatisfied ? SATISFIED : UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertSame(actual, result(positiveSatisfied ? positive : negative));
        assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank());
    }

    private static <E> void assertStatus(
            PreservingCondition<? super List<E>> condition,
            List<? extends E> elements, ConditionEvaluation.Status status)
            throws Exception {
        List<E> actual = new ArrayList<>(elements);
        assertEquals(status, evaluate(condition, actual).status());
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("ordered varargs", containsExactly("a", "a"),
                        doesNotContainExactly("a", "a"),
                        List.of("a", "a"), List.of("a", "b")),
                new Pair("ordered collection",
                        containsExactlyElementsOf(List.of("a", "a")),
                        doesNotContainExactlyElementsOf(List.of("a", "a")),
                        List.of("a", "a"), List.of("a", "b")),
                new Pair("any-order varargs",
                        containsExactlyInAnyOrder("a", "b"),
                        doesNotContainExactlyInAnyOrder("a", "b"),
                        List.of("b", "a"), List.of("a", "a")),
                new Pair("any-order collection",
                        containsExactlyInAnyOrderElementsOf(List.of("a", "b")),
                        doesNotContainExactlyInAnyOrderElementsOf(
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
