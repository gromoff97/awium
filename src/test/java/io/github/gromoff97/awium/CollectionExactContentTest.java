package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ExpectedValue;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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
    void completeRawAndExplainedExactTableIsComplementary() throws Exception {
        for (Pair pair : PAIRS) {
            for (boolean explained : new boolean[] {false, true}) {
                assertPair(pair, pair.matchingActual(), true, explained);
                assertPair(pair, pair.mismatchingActual(), false, explained);
            }
        }
    }

    @Test
    void exactAnyOrderRejectsIterationShorterThanReportedSize()
            throws Exception {
        Collection<String> actual = new AbstractCollection<>() {
            @Override
            public Iterator<String> iterator() {
                return List.<String>of().iterator();
            }

            @Override
            public int size() {
                return 1;
            }
        };

        assertEquals(Evaluation.Status.UNSATISFIED,
                RuntimeCondition.<Collection<String>>preserving(
                                containsExactlyInAnyOrder("a"))
                        .evaluate(actual).status());
        assertEquals(Evaluation.Status.SATISFIED,
                RuntimeCondition.<Collection<String>>preserving(
                                doesNotContainExactlyInAnyOrder("a"))
                        .evaluate(actual).status());
    }

    @Test
    void orderedFormsAreOrderSensitiveAndAnyOrderFormsAreNot()
            throws Exception {
        assertStatus(containsExactly("a", "b"),
                List.of("b", "a"), Evaluation.Status.UNSATISFIED);
        assertStatus(containsExactlyInAnyOrder("a", "b"),
                List.of("b", "a"), Evaluation.Status.SATISFIED);
    }

    @Test
    void cardinalityPrecheckReadsSizesOnceAndTraversesOnlyWhenNeeded()
            throws Exception {
        ExactList<String> differentActual = new ExactList<>(List.of("a"));
        ExactList<String> differentExpected =
                new ExactList<>(List.of("a", "b"));
        Evaluation<?> different = evaluate(
                containsExactlyElementsOf(differentExpected),
                differentActual, false);
        assertEquals(Evaluation.Status.UNSATISFIED, different.status());
        assertAccess(differentActual, 1, 0, 0);
        assertAccess(differentExpected, 1, 0, 0);

        ExactList<String> emptyActual = new ExactList<>(List.of());
        ExactList<String> emptyExpected = new ExactList<>(List.of());
        Evaluation<?> empty = evaluate(
                containsExactlyElementsOf(emptyExpected),
                emptyActual, false);
        assertEquals(Evaluation.Status.SATISFIED, empty.status());
        assertAccess(emptyActual, 1, 0, 0);
        assertAccess(emptyExpected, 1, 0, 0);

        ExactList<String> equalActual = new ExactList<>(List.of("a", "b"));
        ExactList<String> equalExpected =
                new ExactList<>(List.of("a", "b"));
        Evaluation<?> equal = evaluate(
                containsExactlyElementsOf(equalExpected),
                equalActual, false);
        assertEquals(Evaluation.Status.SATISFIED, equal.status());
        assertAccess(equalActual, 1, 1, 2);
        assertAccess(equalExpected, 1, 1, 2);
    }

    @Test
    void diagnosticsDoNotReadCardinalityOrContentAgain() {
        ExactList<String> actual = new ExactList<>(List.of("a"));
        ExactList<String> expected = new ExactList<>(List.of("b"));

        assertThrows(AwaitTimeoutException.class,
                () -> timed(actual).until(
                        containsExactlyElementsOf(expected)
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
        assertEquals(Evaluation.Status.SATISFIED,
                evaluate(arrayCondition,
                        new ExactList<>(List.of("after")), false).status());

        ArrayList<String> values = new ArrayList<>(List.of("before"));
        PreservingCondition<? super ExactList<String>> collectionCondition =
                containsExactlyElementsOf(values);
        values.set(0, "after");
        assertEquals(Evaluation.Status.SATISFIED,
                evaluate(collectionCondition,
                        new ExactList<>(List.of("after")), false).status());

        assertStatus(containsExactly(), List.of(),
                Evaluation.Status.SATISFIED);
        assertStatus(doesNotContainExactly(), List.of(),
                Evaluation.Status.UNSATISFIED);
        assertStatus(containsExactlyInAnyOrderElementsOf(
                List.of()), List.of(), Evaluation.Status.SATISFIED);
    }

    @Test
    void exactFactoriesRejectOnlyNullAggregateReferences() {
        List<Executable> factories = List.of(
                () -> containsExactly((Object[]) null),
                () -> doesNotContainExactly((Object[]) null),
                () -> containsExactlyInAnyOrder(
                        (Object[]) null),
                () -> doesNotContainExactlyInAnyOrder(
                        (Object[]) null),
                () -> containsExactlyElementsOf(
                        (Collection<Object>) null),
                () -> doesNotContainExactlyElementsOf(
                        (Collection<Object>) null),
                () -> containsExactlyInAnyOrderElementsOf(
                        (Collection<Object>) null),
                () -> doesNotContainExactlyInAnyOrderElementsOf(
                        (Collection<Object>) null));

        factories.forEach(factory -> assertEquals(
                "expected elements must not be null",
                assertThrows(NullPointerException.class, factory).getMessage()));
    }

    @Test
    void nullAndIncompatibleElementsUseLibraryEquality() throws Exception {
        String expectedNull = null;
        assertStatus(containsExactly(expectedNull),
                Arrays.asList((String) null), Evaluation.Status.SATISFIED);
        assertStatus(CollectionConditionProvider.<Object>
                        containsExactlyInAnyOrder(1, null),
                Arrays.asList(null, "not an integer"),
                Evaluation.Status.UNSATISFIED);

        int[] actualArray = {1, 2};
        int[] expectedArray = {1, 2};
        assertStatus(containsExactly(expectedArray),
                List.<Object>of(actualArray), Evaluation.Status.SATISFIED);
    }

    @Test
    void equalityIsActualFirstAndNeverHashes() throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);

        assertStatus(containsExactlyInAnyOrder(expected),
                List.of(actual), Evaluation.Status.SATISFIED);

        assertEquals(1, actual.equalsCalls);
        assertEquals(0, expected.equalsCalls);
    }

    @Test
    void anyOrderMatchingIsGreedyInActualAndExpectedEncounterOrder()
            throws Exception {
        GreedyValue first = new GreedyValue("first", Set.of("x", "y"));
        GreedyValue second = new GreedyValue("second", Set.of("x"));
        ExpectedValue x = new ExpectedValue("x");
        ExpectedValue y = new ExpectedValue("y");

        assertStatus(containsExactlyInAnyOrder(x, y),
                List.of(first, second), Evaluation.Status.UNSATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(containsExactlyInAnyOrder(y, x),
                List.of(first, second), Evaluation.Status.SATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);
    }

    @Test
    void negativeExactCannotTurnAccessOrEqualityFailuresIntoSuccess() {
        ExactList<String> actualSize = new ExactList<>(List.of("a"));
        actualSize.sizeFailure = new IllegalStateException("actual size");
        assertFailFast(actualSize, doesNotContainExactlyInAnyOrder("a"),
                actualSize.sizeFailure);

        ExactList<String> expectedSize = new ExactList<>(List.of("a"));
        expectedSize.sizeFailure = new IllegalStateException("expected size");
        assertFailFast(new ExactList<>(List.of("a")),
                doesNotContainExactlyElementsOf(expectedSize),
                expectedSize.sizeFailure);

        ExactList<String> actualIterator = new ExactList<>(List.of("a"));
        actualIterator.iteratorFailure = new IllegalStateException(
                "actual iterator");
        assertFailFast(actualIterator,
                doesNotContainExactly("a"),
                actualIterator.iteratorFailure);

        ExactList<String> actualNext = new ExactList<>(List.of("a"));
        actualNext.nextFailure = new IllegalStateException("actual next");
        assertFailFast(actualNext,
                doesNotContainExactly("a"),
                actualNext.nextFailure);

        ExactList<String> expectedIterator = new ExactList<>(List.of("a"));
        expectedIterator.iteratorFailure = new IllegalStateException(
                "expected iterator");
        assertFailFast(new ExactList<>(List.of("a")),
                doesNotContainExactlyElementsOf(
                        expectedIterator), expectedIterator.iteratorFailure);

        ExactList<String> expectedNext = new ExactList<>(List.of("a"));
        expectedNext.nextFailure = new IllegalStateException("expected next");
        assertFailFast(new ExactList<>(List.of("a")),
                doesNotContainExactlyElementsOf(expectedNext),
                expectedNext.nextFailure);

        ThrowingEquals throwing = new ThrowingEquals(
                new IllegalStateException("equality"));
        assertFailFast(new ExactList<>(List.of(throwing)),
                doesNotContainExactlyInAnyOrder(
                        new ThrowingEquals(null)), throwing.failure);
    }

    @Test
    void nullActualIsUnsatisfiedForBothSignsWithoutExpectedAccess()
            throws Exception {
        ExactList<String> expected = new ExactList<>(List.of("a"));
        assertEquals(Evaluation.Status.UNSATISFIED,
                RuntimeCondition.<ExactList<String>>preserving(
                        containsExactlyElementsOf(expected))
                        .evaluate(null).status());
        assertEquals(Evaluation.Status.UNSATISFIED,
                RuntimeCondition.<ExactList<String>>preserving(
                        doesNotContainExactlyElementsOf(expected))
                        .evaluate(null).status());
        assertAccess(expected, 0, 0, 0);
    }

    private static void assertPair(Pair pair, List<String> elements,
            boolean positiveSatisfied, boolean explained) throws Exception {
        ExactList<String> positiveActual = new ExactList<>(elements);
        ExactList<String> negativeActual = new ExactList<>(elements);
        Evaluation<?> positive = evaluate(pair.positive(), positiveActual,
                explained);
        Evaluation<?> negative = evaluate(pair.negative(), negativeActual,
                explained);

        assertEquals(positiveSatisfied ? Evaluation.Status.SATISFIED
                        : Evaluation.Status.UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertAccess(positiveActual, 1, 1, 2);
        assertAccess(negativeActual, 1, 1, 2);
    }

    private static <E> void assertStatus(
            PreservingCondition<? super ExactList<E>> condition,
            List<? extends E> elements, Evaluation.Status expected)
            throws Exception {
        ExactList<E> actual = new ExactList<>(elements);
        assertEquals(expected, evaluate(condition, actual, false).status());
    }

    private static <E> Evaluation<?> evaluate(
            PreservingCondition<? super ExactList<E>> condition,
            ExactList<E> actual, boolean explained) throws Exception {
        RuntimeCondition<ExactList<E>, ExactList<E>> runtime = explained
                ? preserving(condition.because("reason"))
                : preserving(condition);
        assertEquals(explained ? "reason" : null, runtime.explanation());
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

    private static StructuralAwait<ExactList<String>> timed(
            ExactList<String> actual) {
        FakeTime time = new FakeTime(0);
        return new StructuralAwaitStage<>(
                (CollectionSource<ExactList<String>>) () -> {
                    time.advanceNanos(2);
                    return actual;
                }, Collection::size,
                defaults().withEvery(Duration.ofNanos(1))
                .withUpTo(Duration.ofNanos(2)), time, time);
    }

    private record Pair(String name,
            PreservingCondition<? super ExactList<String>> positive,
            PreservingCondition<? super ExactList<String>> negative,
            List<String> matchingActual, List<String> mismatchingActual) {
    }

    private static final class ExactList<E> extends AbstractList<E> {
        private final List<? extends E> elements;
        private RuntimeException sizeFailure;
        private RuntimeException iteratorFailure;
        private RuntimeException nextFailure;
        private int sizeCalls;
        private int iteratorCalls;
        private int nextCalls;

        private ExactList(List<? extends E> elements) {
            this.elements = elements;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
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
                    if (nextFailure != null) {
                        throw nextFailure;
                    }
                    return delegate.next();
                }
            };
        }

        @Override
        public boolean contains(Object value) {
            throw new AssertionError("contains must not be called");
        }

        @Override
        public boolean equals(Object value) {
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return "exact list";
        }
    }

    private static final class GreedyValue {
        private final String name;
        private final Set<String> matches;
        private int equalsCalls;

        private GreedyValue(String name, Set<String> matches) {
            this.name = name;
            this.matches = matches;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof ExpectedValue expected
                    && matches.contains(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
