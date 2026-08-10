package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.diagnostic.*;

import io.github.gromoff97.awium.internal.engine.*;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class CollectionExactContentTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("containsExactly",
                    AwaitConditions.containsExactly("a", "a"),
                    AwaitConditions.doesNotContainExactly("a", "a"),
                    List.of("a", "a"), List.of("a", "b")),
            new Pair("containsExactlyElementsOf",
                    AwaitConditions.containsExactlyElementsOf(
                            List.of("a", "a")),
                    AwaitConditions.doesNotContainExactlyElementsOf(
                            List.of("a", "a")),
                    List.of("a", "a"), List.of("a", "b")),
            new Pair("containsExactlyInAnyOrder",
                    AwaitConditions.containsExactlyInAnyOrder("a", "b"),
                    AwaitConditions.doesNotContainExactlyInAnyOrder("a", "b"),
                    List.of("b", "a"), List.of("a", "a")),
            new Pair("containsExactlyInAnyOrderElementsOf",
                    AwaitConditions.containsExactlyInAnyOrderElementsOf(
                            List.of("a", "b")),
                    AwaitConditions.doesNotContainExactlyInAnyOrderElementsOf(
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
    void negativeAnyOrderAggregateFactoryReturnsAUsablePublicCondition() {
        assertNotNull(AwaitConditions.doesNotContainExactlyInAnyOrderElementsOf(
                List.of("a")));
    }

    @Test
    void orderedFormsAreOrderSensitiveAndAnyOrderFormsAreNot()
            throws Exception {
        assertStatus(AwaitConditions.containsExactly("a", "b"),
                List.of("b", "a"), Evaluation.Status.UNSATISFIED);
        assertStatus(AwaitConditions.containsExactlyInAnyOrder("a", "b"),
                List.of("b", "a"), Evaluation.Status.SATISFIED);
    }

    @Test
    void cardinalityPrecheckReadsSizesOnceAndTraversesOnlyWhenNeeded()
            throws Exception {
        ExactList<String> differentActual = new ExactList<>(List.of("a"));
        ExpectedCollection<String> differentExpected =
                new ExpectedCollection<>(List.of("a", "b"));
        Evaluation<?> different = evaluate(
                AwaitConditions.containsExactlyElementsOf(differentExpected),
                differentActual, false);
        assertEquals(Evaluation.Status.UNSATISFIED, different.status());
        assertAccess(differentActual, 1, 0, 0);
        assertExpectedAccess(differentExpected, 1, 0, 0);

        ExactList<String> emptyActual = new ExactList<>(List.of());
        ExpectedCollection<String> emptyExpected =
                new ExpectedCollection<>(List.of());
        Evaluation<?> empty = evaluate(
                AwaitConditions.containsExactlyElementsOf(emptyExpected),
                emptyActual, false);
        assertEquals(Evaluation.Status.SATISFIED, empty.status());
        assertAccess(emptyActual, 1, 0, 0);
        assertExpectedAccess(emptyExpected, 1, 0, 0);

        ExactList<String> equalActual = new ExactList<>(List.of("a", "b"));
        ExpectedCollection<String> equalExpected =
                new ExpectedCollection<>(List.of("a", "b"));
        Evaluation<?> equal = evaluate(
                AwaitConditions.containsExactlyElementsOf(equalExpected),
                equalActual, false);
        assertEquals(Evaluation.Status.SATISFIED, equal.status());
        assertAccess(equalActual, 1, 1, 2);
        assertExpectedAccess(equalExpected, 1, 1, 2);
    }

    @Test
    void diagnosticsDoNotReadCardinalityOrContentAgain() {
        ExactList<String> actual = new ExactList<>(List.of("a"));
        ExpectedCollection<String> expected =
                new ExpectedCollection<>(List.of("b"));

        assertThrows(AwaitTimeoutException.class,
                () -> timed(actual).until(
                        AwaitConditions.containsExactlyElementsOf(expected)
                                .because("required")));

        assertAccess(actual, 1, 1, 1);
        assertExpectedAccess(expected, 1, 1, 1);
    }

    @Test
    void exactInputsAreRetainedByReferenceAndEmptyInputsAreValid()
            throws Exception {
        String[] array = {"before"};
        PreservingCondition<?> arrayCondition =
                AwaitConditions.containsExactly(array);
        array[0] = "after";
        assertEquals(Evaluation.Status.SATISFIED,
                evaluate(castString(arrayCondition),
                        new ExactList<>(List.of("after")), false).status());

        ArrayList<String> values = new ArrayList<>(List.of("before"));
        PreservingCondition<?> collectionCondition =
                AwaitConditions.containsExactlyElementsOf(values);
        values.set(0, "after");
        assertEquals(Evaluation.Status.SATISFIED,
                evaluate(castString(collectionCondition),
                        new ExactList<>(List.of("after")), false).status());

        assertStatus(AwaitConditions.containsExactly(), List.of(),
                Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.doesNotContainExactly(), List.of(),
                Evaluation.Status.UNSATISFIED);
        assertStatus(AwaitConditions.containsExactlyInAnyOrderElementsOf(
                List.of()), List.of(), Evaluation.Status.SATISFIED);
    }

    @Test
    void exactFactoriesRejectOnlyNullAggregateReferences() {
        List<Executable> factories = List.of(
                () -> AwaitConditions.containsExactly((Object[]) null),
                () -> AwaitConditions.doesNotContainExactly((Object[]) null),
                () -> AwaitConditions.containsExactlyInAnyOrder(
                        (Object[]) null),
                () -> AwaitConditions.doesNotContainExactlyInAnyOrder(
                        (Object[]) null),
                () -> AwaitConditions.containsExactlyElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.doesNotContainExactlyElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.containsExactlyInAnyOrderElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.doesNotContainExactlyInAnyOrderElementsOf(
                        (Collection<Object>) null));

        factories.forEach(factory -> assertEquals(
                "expected elements must not be null",
                assertThrows(NullPointerException.class, factory).getMessage()));
    }

    @Test
    void nullAndIncompatibleElementsUseLibraryEquality() throws Exception {
        String expectedNull = null;
        assertStatus(AwaitConditions.containsExactly(expectedNull),
                Arrays.asList((String) null), Evaluation.Status.SATISFIED);
        assertStatus(AwaitConditions.<Object>containsExactlyInAnyOrder(1, null),
                Arrays.asList(null, "not an integer"),
                Evaluation.Status.UNSATISFIED);

        int[] actualArray = {1, 2};
        int[] expectedArray = {1, 2};
        assertStatus(AwaitConditions.containsExactly(expectedArray),
                List.<Object>of(actualArray), Evaluation.Status.SATISFIED);
    }

    @Test
    void equalityIsActualFirstAndNeverHashes() throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);

        assertStatus(AwaitConditions.containsExactlyInAnyOrder(expected),
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

        assertStatus(AwaitConditions.containsExactlyInAnyOrder(x, y),
                List.of(first, second), Evaluation.Status.UNSATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);

        first.equalsCalls = 0;
        second.equalsCalls = 0;
        assertStatus(AwaitConditions.containsExactlyInAnyOrder(y, x),
                List.of(first, second), Evaluation.Status.SATISFIED);
        assertEquals(2, first.equalsCalls + second.equalsCalls);
        assertEquals(0, x.equalsCalls + y.equalsCalls);
    }

    @Test
    void negativeExactCannotTurnAccessOrEqualityFailuresIntoSuccess() {
        assertFailFast(actualWithSizeFailure("actual size"),
                AwaitConditions.doesNotContainExactlyInAnyOrder("a"));

        ExpectedCollection<String> expectedSize =
                new ExpectedCollection<>(List.of("a"));
        expectedSize.sizeFailure = new IllegalStateException("expected size");
        assertFailFast(new ExactList<>(List.of("a")),
                AwaitConditions.doesNotContainExactlyElementsOf(expectedSize),
                expectedSize.sizeFailure);

        ExactList<String> actualIterator = new ExactList<>(List.of("a"));
        actualIterator.iteratorFailure = new IllegalStateException(
                "actual iterator");
        assertFailFast(actualIterator,
                AwaitConditions.doesNotContainExactly("a"),
                actualIterator.iteratorFailure);

        ExactList<String> actualNext = new ExactList<>(List.of("a"));
        actualNext.nextFailure = new IllegalStateException("actual next");
        assertFailFast(actualNext,
                AwaitConditions.doesNotContainExactly("a"),
                actualNext.nextFailure);

        ExpectedCollection<String> expectedIterator =
                new ExpectedCollection<>(List.of("a"));
        expectedIterator.iteratorFailure = new IllegalStateException(
                "expected iterator");
        assertFailFast(new ExactList<>(List.of("a")),
                AwaitConditions.doesNotContainExactlyElementsOf(
                        expectedIterator), expectedIterator.iteratorFailure);

        ExpectedCollection<String> expectedNext =
                new ExpectedCollection<>(List.of("a"));
        expectedNext.nextFailure = new IllegalStateException("expected next");
        assertFailFast(new ExactList<>(List.of("a")),
                AwaitConditions.doesNotContainExactlyElementsOf(expectedNext),
                expectedNext.nextFailure);

        ThrowingEquals throwing = new ThrowingEquals(
                new IllegalStateException("equality"));
        assertFailFast(new ExactList<>(List.of(throwing)),
                AwaitConditions.doesNotContainExactlyInAnyOrder(
                        new ThrowingEquals(null)), throwing.failure);
    }

    @Test
    void nullActualIsUnsatisfiedForBothSignsWithoutExpectedAccess()
            throws Exception {
        ExpectedCollection<String> expected =
                new ExpectedCollection<>(List.of("a"));
        assertEquals(Evaluation.Status.UNSATISFIED,
                ConditionRuntime.<ExactList<String>>preserving(
                        AwaitConditions.containsExactlyElementsOf(expected))
                        .evaluate(null).status());
        assertEquals(Evaluation.Status.UNSATISFIED,
                ConditionRuntime.<ExactList<String>>preserving(
                        AwaitConditions.doesNotContainExactlyElementsOf(expected))
                        .evaluate(null).status());
        assertExpectedAccess(expected, 0, 0, 0);
    }

    @Test
    void exactGenericVarargsFactoriesAreSafeVarargs() {
        Set<String> names = Set.of(
                "containsExactly", "doesNotContainExactly",
                "containsExactlyInAnyOrder",
                "doesNotContainExactlyInAnyOrder");
        Set<String> discovered = Arrays.stream(
                        AwaitConditions.class.getDeclaredMethods())
                .filter(method -> method.isVarArgs()
                        && names.contains(method.getName()))
                .peek(method -> {
                    assertTrue(Modifier.isStatic(method.getModifiers()));
                    assertTrue(method.isAnnotationPresent(SafeVarargs.class),
                            method.getName());
                })
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(names, discovered);
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
        assertEquals(0, actual.containsCalls);
        assertEquals(0, actual.equalsCalls);
        assertEquals(0, actual.hashCodeCalls);
    }

    private static <E> Evaluation<?> evaluate(
            PreservingCondition<? super ExactList<E>> condition,
            ExactList<E> actual, boolean explained) throws Exception {
        ConditionRuntime<ExactList<E>, ExactList<E>> runtime = explained
                ? ConditionRuntime.preserving(condition.because("reason"))
                : ConditionRuntime.preserving(condition);
        assertEquals(explained ? "reason" : null, runtime.explanation());
        assertFalse(runtime.description().get().isBlank());
        return runtime.evaluate(actual);
    }

    @SuppressWarnings("unchecked")
    private static PreservingCondition<? super ExactList<String>> castString(
            PreservingCondition<?> condition) {
        return (PreservingCondition<? super ExactList<String>>) condition;
    }

    private static ExactList<String> actualWithSizeFailure(String message) {
        ExactList<String> actual = new ExactList<>(List.of("a"));
        actual.sizeFailure = new IllegalStateException(message);
        return actual;
    }

    private static <E> void assertFailFast(ExactList<E> actual,
            PreservingCondition<? super ExactList<E>> condition) {
        assertFailFast(actual, condition, actual.sizeFailure);
    }

    private static <E> void assertFailFast(ExactList<E> actual,
            PreservingCondition<? super ExactList<E>> condition,
            RuntimeException cause) {
        AwaitConditionEvaluationException failure = assertThrows(
                AwaitConditionEvaluationException.class,
                () -> Awium.await((AwaitSources.SequencedCollectionSource<
                        E, ExactList<E>>) () -> actual).until(condition));
        assertSame(cause, failure.getCause());
    }

    private static void assertAccess(ExactList<?> actual, int size,
            int iterators, int next) {
        assertEquals(size, actual.sizeCalls);
        assertEquals(iterators, actual.iteratorCalls);
        assertEquals(next, actual.nextCalls);
        assertEquals(0, actual.getCalls);
    }

    private static void assertExpectedAccess(ExpectedCollection<?> expected,
            int size, int iterators, int next) {
        assertEquals(size, expected.sizeCalls);
        assertEquals(iterators, expected.iteratorCalls);
        assertEquals(next, expected.nextCalls);
    }

    private static SequencedCollectionAwait.Until<String, ExactList<String>> timed(
            ExactList<String> actual) {
        FakeTime time = new FakeTime(0);
        AwaitChain<ExactList<String>> chain = new AwaitChain<>(() -> {
            time.advanceNanos(2);
            return actual;
        }, WaitConfiguration.defaults().withEvery(Duration.ofNanos(1))
                .withUpTo(Duration.ofNanos(2)), time, time,
                new Interrupts(), new FailureFactory());
        return new SequencedCollectionStages
                .SequencedCollectionAfterUpToStage<>(chain);
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
        private int getCalls;
        private int containsCalls;
        private int equalsCalls;
        private int hashCodeCalls;

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
            getCalls++;
            return elements.get(index);
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
            containsCalls++;
            throw new AssertionError("contains must not be called");
        }

        @Override
        public boolean equals(Object value) {
            equalsCalls++;
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            hashCodeCalls++;
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return "exact list";
        }
    }

    private static final class ExpectedCollection<E>
            extends AbstractCollection<E> {
        private final List<? extends E> elements;
        private RuntimeException sizeFailure;
        private RuntimeException iteratorFailure;
        private RuntimeException nextFailure;
        private int sizeCalls;
        private int iteratorCalls;
        private int nextCalls;

        private ExpectedCollection(List<? extends E> elements) {
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
                    if (!delegate.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return delegate.next();
                }
            };
        }
    }

    private static final class Directional {
        private final boolean equalsResult;
        private int equalsCalls;

        private Directional(boolean equalsResult) {
            this.equalsResult = equalsResult;
        }

        @Override
        public boolean equals(Object value) {
            equalsCalls++;
            return value instanceof Directional && equalsResult;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    private static final class ExpectedValue {
        private final String value;
        private int equalsCalls;

        private ExpectedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof ExpectedValue expected
                    && value.equals(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
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

    private static final class ThrowingEquals {
        private final RuntimeException failure;

        private ThrowingEquals(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean equals(Object other) {
            if (failure != null) {
                throw failure;
            }
            return other instanceof ThrowingEquals;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }
}
