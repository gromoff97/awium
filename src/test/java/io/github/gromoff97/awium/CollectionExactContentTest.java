package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ExpectedValue;
import static io.github.gromoff97.awium.ProbeContainers.GreedyValue;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedStructuralAwait;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionExactContentTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("containsExactly",
                    containsExactly("a", "a"),
                    doesNotContainExactly("a", "a"),
                    List.of("a", "a"), List.of("a", "b")),
            new Pair("containsExactlyElementsOf",
                    containsExactlyElementsOf(
                            List.of("a", "a")),
                    doesNotContainExactlyElementsOf(
                            List.of("a", "a")),
                    List.of("a", "a"), List.of("a", "b")),
            new Pair("containsExactlyInAnyOrder",
                    containsExactlyInAnyOrder("a", "b"),
                    doesNotContainExactlyInAnyOrder("a", "b"),
                    List.of("b", "a"), List.of("a", "a")),
            new Pair("containsExactlyInAnyOrderElementsOf",
                    containsExactlyInAnyOrderElementsOf(
                            List.of("a", "b")),
                    doesNotContainExactlyInAnyOrderElementsOf(
                            List.of("a", "b")),
                    List.of("b", "a"), List.of("a", "a")));

    @Test
    void completeRawExactTableIsComplementary() throws Exception {
        for (Pair pair : PAIRS) {
            assertPair(pair, pair.matchingActual(), true);
            assertPair(pair, pair.mismatchingActual(), false);
        }
    }

    @Test
    void exactAnyOrderRejectsTwoSidedIterationLengthMismatch()
            throws Exception {
        for (var elements : List.of(List.<String>of(), List.of("a", "b"))) {
            Collection<String> actual = reportedCollection(elements, 1);
            Collection<String> expected = reportedCollection(elements, 1);
            assertEquals(UNSATISFIED,
                    RuntimeCondition.<Collection<String>>preserving(
                                    containsExactlyInAnyOrderElementsOf(expected))
                            .evaluate(actual).status());
        }
    }

    @Test
    void exactAnyOrderPreservesExpectedIteratorSurplus() throws Exception {
        Collection<String> actual = reportedCollection(List.of("a"), 1);
        Collection<String> expected = reportedCollection(
                List.of("a", "b"), 1);

        assertEquals(SATISFIED,
                RuntimeCondition.<Collection<String>>preserving(
                                containsExactlyInAnyOrderElementsOf(expected))
                        .evaluate(actual).status());
    }

    @Test
    void orderedFormsAreOrderSensitiveAndAnyOrderFormsAreNot()
            throws Exception {
        assertStatus(containsExactly("a", "b"),
                List.of("b", "a"), UNSATISFIED);
        assertStatus(containsExactlyInAnyOrder("a", "b"),
                List.of("b", "a"), SATISFIED);
    }

    @Test
    void cardinalityPrecheckReadsSizesOnceAndTraversesOnlyWhenNeeded()
            throws Exception {
        ExactList<String> differentActual = new ExactList<>(List.of("a"));
        ExactList<String> differentExpected =
                new ExactList<>(List.of("a", "b"));
        Evaluation<?> different = evaluate(
                containsExactlyElementsOf(differentExpected),
                differentActual);
        assertEquals(UNSATISFIED, different.status());
        assertAccess(differentActual, 1, 0, 0);
        assertAccess(differentExpected, 1, 0, 0);

        ExactList<String> emptyActual = new ExactList<>(List.of());
        ExactList<String> emptyExpected = new ExactList<>(List.of());
        Evaluation<?> empty = evaluate(
                containsExactlyElementsOf(emptyExpected),
                emptyActual);
        assertEquals(SATISFIED, empty.status());
        assertAccess(emptyActual, 1, 0, 0);
        assertAccess(emptyExpected, 1, 0, 0);

        ExactList<String> equalActual = new ExactList<>(List.of("a", "b"));
        ExactList<String> equalExpected =
                new ExactList<>(List.of("a", "b"));
        Evaluation<?> equal = evaluate(
                containsExactlyElementsOf(equalExpected),
                equalActual);
        assertEquals(SATISFIED, equal.status());
        assertAccess(equalActual, 1, 1, 2);
        assertAccess(equalExpected, 1, 1, 2);
    }

    @Test
    void diagnosticsDoNotReadCardinalityOrContentAgain() {
        ExactList<String> actual = new ExactList<>(List.of("a"));
        ExactList<String> expected = new ExactList<>(List.of("b"));
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedStructuralAwait(
                        (Source<ExactList<String>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, "collection", Collection::size,
                        defaults().withEvery(ofNanos(1))
                                .withUpTo(ofNanos(2)), time, time)
                        .until(containsExactlyElementsOf(expected)
                                .because("required")));

        assertAccess(actual, 1, 1, 1);
        assertAccess(expected, 1, 1, 1);
    }

    @Test
    void exactInputsAreRetainedByReferenceAndEmptyInputsAreValid()
            throws Exception {
        String[] array = {"before"};
        PreservingCondition<? super ExactList<String>> arrayCondition =
                containsExactly(array);
        array[0] = "after";
        assertEquals(SATISFIED,
                evaluate(arrayCondition,
                        new ExactList<>(List.of("after"))).status());

        ArrayList<String> values = new ArrayList<>(List.of("before"));
        PreservingCondition<? super ExactList<String>> collectionCondition =
                containsExactlyElementsOf(values);
        values.set(0, "after");
        assertEquals(SATISFIED,
                evaluate(collectionCondition,
                        new ExactList<>(List.of("after"))).status());

        assertStatus(containsExactly(), List.of(),
                SATISFIED);
    }

    @Test
    void exactFactoriesRejectOnlyNullAggregateReferences() {
        assertEquals(
                "expected elements must not be null",
                assertThrows(NullPointerException.class,
                        () -> containsExactly((Object[]) null)).getMessage());
    }

    @Test
    void nullAndArrayElementsUseLibraryEquality() throws Exception {
        String expectedNull = null;
        assertStatus(containsExactly(expectedNull),
                asList((String) null), SATISFIED);

        int[] actualArray = {1, 2};
        int[] expectedArray = {1, 2};
        assertStatus(containsExactly(expectedArray),
                List.<Object>of(actualArray), SATISFIED);
    }

    @Test
    void equalityIsActualFirstAndNeverHashes() throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);

        assertStatus(containsExactlyInAnyOrder(expected),
                List.of(actual), SATISFIED);

        assertEquals(1, actual.equalsCalls);
    }

    @Test
    void anyOrderMatchingIsGreedyInActualAndExpectedEncounterOrder()
            throws Exception {
        GreedyValue first = new GreedyValue(Set.of("x", "y"));
        GreedyValue second = new GreedyValue(Set.of("x"));
        ExpectedValue x = new ExpectedValue("x");
        ExpectedValue y = new ExpectedValue("y");

        assertStatus(containsExactlyInAnyOrder(x, y),
                List.of(first, second), UNSATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(containsExactlyInAnyOrder(y, x),
                List.of(first, second), SATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
    }

    @Test
    void negativeExactCannotTurnAccessOrEqualityFailuresIntoSuccess() {
        ExactList<String> actualIterator = new ExactList<>(List.of("a"));
        actualIterator.iteratorFailure = new IllegalStateException(
                "actual iterator");
        assertFailFast(actualIterator,
                doesNotContainExactly("a"),
                actualIterator.iteratorFailure);

        ThrowingEquals throwing = new ThrowingEquals(
                new IllegalStateException("equality"));
        assertFailFast(new ExactList<>(List.of(throwing)),
                doesNotContainExactlyInAnyOrder(
                        new ThrowingEquals(null)), throwing.failure);
    }

    @Test
    void nullActualIsUnsatisfiedWithoutExactExpectedAccess()
            throws Exception {
        ExactList<String> expected = new ExactList<>(List.of("a"));
        assertEquals(UNSATISFIED,
                RuntimeCondition.<ExactList<String>>preserving(
                        containsExactlyElementsOf(expected))
                        .evaluate(null).status());
        assertAccess(expected, 0, 0, 0);
    }

    private static void assertPair(Pair pair, List<String> elements,
            boolean positiveSatisfied) throws Exception {
        ExactList<String> positiveActual = new ExactList<>(elements);
        ExactList<String> negativeActual = new ExactList<>(elements);
        Evaluation<?> positive = evaluate(pair.positive(), positiveActual);
        Evaluation<?> negative = evaluate(pair.negative(), negativeActual);

        assertEquals(positiveSatisfied ? SATISFIED
                        : UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertAccess(positiveActual, 1, 1, 2);
        assertAccess(negativeActual, 1, 1, 2);
    }

    private static <E> void assertStatus(
            PreservingCondition<? super ExactList<E>> condition,
            List<? extends E> elements, Status expected)
            throws Exception {
        ExactList<E> actual = new ExactList<>(elements);
        assertEquals(expected, evaluate(condition, actual).status());
    }

    private static <E> Evaluation<?> evaluate(
            PreservingCondition<? super ExactList<E>> condition,
            ExactList<E> actual) throws Exception {
        RuntimeCondition<ExactList<E>, ExactList<E>> runtime =
                preserving(condition);
        assertFalse(runtime.description().get().isBlank());
        return runtime.evaluate(actual);
    }

    private static <E> void assertFailFast(ExactList<E> actual,
            PreservingCondition<? super ExactList<E>> condition,
            RuntimeException cause) {
        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await((CollectionSource<ExactList<E>>) () -> actual)
                        .until(condition));
        assertSame(cause, failure.getCause());
    }

    private static void assertAccess(ExactList<?> actual, int size,
            int iterators, int next) {
        assertEquals(size, actual.sizeCalls);
        assertEquals(iterators, actual.iteratorCalls);
        assertEquals(next, actual.nextCalls);
    }

    private static <E> Collection<E> reportedCollection(
            List<E> elements, int reportedSize) {
        return new AbstractCollection<>() {
            @Override
            public Iterator<E> iterator() {
                return elements.iterator();
            }

            @Override
            public int size() {
                return reportedSize;
            }
        };
    }

    private record Pair(String name,
            PreservingCondition<? super ExactList<String>> positive,
            PreservingCondition<? super ExactList<String>> negative,
            List<String> matchingActual, List<String> mismatchingActual) {
    }

    private static final class ExactList<E> extends AbstractList<E> {
        private final List<? extends E> elements;
        private RuntimeException iteratorFailure;
        private int sizeCalls;
        private int iteratorCalls;
        private int nextCalls;

        private ExactList(List<? extends E> elements) {
            this.elements = elements;
        }

        @Override
        public int size() {
            sizeCalls++;
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
            Iterator<? extends E> delegate = elements.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public E next() {
                    nextCalls++;
                    return delegate.next();
                }
            };
        }

        @Override
        public String toString() {
            return "exact list";
        }
    }
}
