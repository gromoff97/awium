package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.engine.*;

import io.github.gromoff97.awium.exception.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CollectionMembershipTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("contains", AwaitConditions.contains("b"),
                    AwaitConditions.doesNotContain("b"),
                    List.of("a", "b", "c"), List.of("a", "c"), 2, 2),
            new Pair("containsAll", AwaitConditions.containsAll("a", "b"),
                    AwaitConditions.doesNotContainAll("a", "b"),
                    List.of("a", "b", "c"), List.of("a", "c"), 2, 2),
            new Pair("containsAllElementsOf",
                    AwaitConditions.containsAllElementsOf(List.of("a", "b")),
                    AwaitConditions.doesNotContainAllElementsOf(
                            List.of("a", "b")),
                    List.of("a", "b", "c"), List.of("a", "c"), 2, 2),
            new Pair("containsAnyOf", AwaitConditions.containsAnyOf("x", "b"),
                    AwaitConditions.containsNoneOf("x", "b"),
                    List.of("a", "b", "c"), List.of("a", "c"), 2, 2),
            new Pair("containsAnyElementsOf",
                    AwaitConditions.containsAnyElementsOf(List.of("x", "b")),
                    AwaitConditions.containsNoElementsOf(List.of("x", "b")),
                    List.of("a", "b", "c"), List.of("a", "c"), 2, 2));

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeRawAndExplainedMembershipTableIsComplementary()
            throws Exception {
        for (Pair pair : PAIRS) {
            for (boolean explained : new boolean[] {false, true}) {
                assertPair(pair, pair.matchingActual(), true,
                        pair.matchingNextCalls(), explained);
                assertPair(pair, pair.mismatchingActual(), false,
                        pair.mismatchingNextCalls(), explained);
            }
        }
    }

    @Test
    void aggregateAnyFactoriesReturnUsablePublicConditions() {
        assertNotNull(AwaitConditions.containsAnyElementsOf(List.of("a")));
        assertNotNull(AwaitConditions.containsNoElementsOf(List.of("a")));
    }

    @Test
    void nullActualIsUnsatisfiedForBothSigns() throws Exception {
        for (Pair pair : PAIRS) {
            for (boolean explained : new boolean[] {false, true}) {
                assertNullActual(runtime(pair.positive(), explained));
                assertNullActual(runtime(pair.negative(), explained));
            }
        }
    }

    @Test
    void repeatedExpectedPositionsAreSetLikeForBothAllForms()
            throws Exception {
        List<Pair> repeated = List.of(
                new Pair("containsAll", AwaitConditions.containsAll("a", "a"),
                        AwaitConditions.doesNotContainAll("a", "a"),
                        List.of("a"), List.of("b"), 1, 1),
                new Pair("containsAllElementsOf",
                        AwaitConditions.containsAllElementsOf(List.of("a", "a")),
                        AwaitConditions.doesNotContainAllElementsOf(
                                List.of("a", "a")),
                        List.of("a"), List.of("b"), 1, 1));

        for (Pair pair : repeated) {
            assertPair(pair, pair.matchingActual(), true, 1, false);
            assertPair(pair, pair.matchingActual(), true, 1, true);
        }
    }

    @Test
    void valueEqualityIsActualFirstArrayAwareAndNeverHashes() throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);
        var directional = new ProbeContainers.MembershipCollection<>(
                List.of(actual));
        var arrays = new ProbeContainers.MembershipCollection<Object>(
                List.of(new int[] {1, 2}));

        assertSatisfied(ConditionAdapters.<
                ProbeContainers.MembershipCollection<Directional>>preserving(
                        AwaitConditions.contains(expected)).evaluate(directional),
                directional);
        assertSatisfied(ConditionAdapters.<
                ProbeContainers.MembershipCollection<Object>>preserving(
                        AwaitConditions.contains(new int[] {1, 2})).evaluate(arrays),
                arrays);

        assertEquals(1, actual.equalsCalls);
        assertEquals(0, expected.equalsCalls);
        assertOnlyIterator(directional, 1);
        assertOnlyIterator(arrays, 1);
    }

    @Test
    void positiveAndNegativeFormsMakeIdenticalUserCalls() throws Exception {
        CountingValue expected = new CountingValue("b");
        CountingValue positiveFirst = new CountingValue("a");
        CountingValue positiveSecond = new CountingValue("b");
        CountingValue negativeFirst = new CountingValue("a");
        CountingValue negativeSecond = new CountingValue("b");
        var positiveActual = new ProbeContainers.MembershipCollection<>(
                List.of(positiveFirst, positiveSecond));
        var negativeActual = new ProbeContainers.MembershipCollection<>(
                List.of(negativeFirst, negativeSecond));

        Evaluation<?> positive = ConditionAdapters.<
                ProbeContainers.MembershipCollection<CountingValue>>preserving(
                        AwaitConditions.containsAnyOf(expected)).evaluate(
                                positiveActual);
        Evaluation<?> negative = ConditionAdapters.<
                ProbeContainers.MembershipCollection<CountingValue>>preserving(
                        AwaitConditions.containsNoneOf(expected)).evaluate(
                                negativeActual);

        assertNotEquals(positive.status(), negative.status());
        assertEquals(positiveFirst.equalsCalls + positiveSecond.equalsCalls,
                negativeFirst.equalsCalls + negativeSecond.equalsCalls);
        assertEquals(positiveActual.hasNextCalls, negativeActual.hasNextCalls);
        assertEquals(positiveActual.nextCalls, negativeActual.nextCalls);
        assertOnlyIterator(positiveActual, 2);
        assertOnlyIterator(negativeActual, 2);
    }

    @Test
    void terminalDiagnosticsDoNotTraverseAgain() {
        var raw = new ProbeContainers.MembershipCollection<>(
                List.of("a", "b"));
        var explained = new ProbeContainers.MembershipCollection<>(
                List.of("a", "b"));

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollection(raw).until(
                        AwaitConditions.contains("missing")));
        assertThrows(AwaitTimeoutException.class,
                () -> timedCollection(explained).until(
                        AwaitConditions.doesNotContain("a").because("required")));

        assertOnlyIterator(raw, 2);
        assertOnlyIterator(explained, 1);
    }

    @Test
    void traversalAndEqualityFailuresAreFailFastForNegativeForms() {
        RuntimeException acquisitionCause = new IllegalStateException(
                "iterator failed");
        RuntimeException advancementCause = new IllegalStateException(
                "next failed");
        RuntimeException equalityCause = new IllegalStateException(
                "equals failed");
        var acquisition = new ProbeContainers.MembershipCollection<String>(
                acquisitionCause);
        var advancement = new ProbeContainers.MembershipCollection<>(
                List.of("a"), 1, advancementCause);
        var equality = new ProbeContainers.MembershipCollection<>(
                List.of(new ThrowingEquals(equalityCause)));

        assertSame(acquisitionCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(acquisition,
                        AwaitConditions.doesNotContain("a"))).getCause());
        assertSame(advancementCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(advancement,
                        AwaitConditions.containsNoneOf("a"))).getCause());
        assertSame(equalityCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(equality,
                        AwaitConditions.doesNotContain(
                                new ThrowingEquals(null)))).getCause());
    }

    @Test
    void aggregateFactoriesRejectNullAndEmptyInputsWithExactMessages() {
        List<Executable> nullVarargs = List.of(
                () -> AwaitConditions.containsAll((Object[]) null),
                () -> AwaitConditions.doesNotContainAll((Object[]) null),
                () -> AwaitConditions.containsAnyOf((Object[]) null),
                () -> AwaitConditions.containsNoneOf((Object[]) null));
        List<Executable> nullCollections = List.of(
                () -> AwaitConditions.containsAllElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.doesNotContainAllElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.containsAnyElementsOf(
                        (Collection<Object>) null),
                () -> AwaitConditions.containsNoElementsOf(
                        (Collection<Object>) null));
        List<Executable> emptyVarargs = List.of(
                () -> AwaitConditions.containsAll(new Object[0]),
                () -> AwaitConditions.doesNotContainAll(new Object[0]),
                () -> AwaitConditions.containsAnyOf(new Object[0]),
                () -> AwaitConditions.containsNoneOf(new Object[0]));
        List<Executable> emptyCollections = List.of(
                () -> AwaitConditions.containsAllElementsOf(List.of()),
                () -> AwaitConditions.doesNotContainAllElementsOf(List.of()),
                () -> AwaitConditions.containsAnyElementsOf(List.of()),
                () -> AwaitConditions.containsNoElementsOf(List.of()));

        nullVarargs.forEach(factory -> assertFailure(factory,
                NullPointerException.class,
                "expected elements must not be null"));
        nullCollections.forEach(factory -> assertFailure(factory,
                NullPointerException.class,
                "expected elements must not be null"));
        emptyVarargs.forEach(factory -> assertFailure(factory,
                IllegalArgumentException.class,
                "expected elements must not be empty"));
        emptyCollections.forEach(factory -> assertFailure(factory,
                IllegalArgumentException.class,
                "expected elements must not be empty"));
    }

    @Test
    void typedNullIsOneValidExpectedElement() throws Exception {
        String expected = null;
        var actual = new ProbeContainers.MembershipCollection<String>(
                Arrays.asList((String) null));

        assertSatisfied(runtime(AwaitConditions.containsAll(expected), false)
                .evaluate(actual), actual);
        assertOnlyIterator(actual, 1);
    }

    @Test
    void elementsOfFactoriesCallOnlyIsEmptyOnceAtConstruction() {
        List<Function<Collection<String>, ?>> factories = List.of(
                AwaitConditions::containsAllElementsOf,
                AwaitConditions::doesNotContainAllElementsOf,
                AwaitConditions::containsAnyElementsOf,
                AwaitConditions::containsNoElementsOf);

        for (Function<Collection<String>, ?> factory : factories) {
            var expected = new ProbeContainers.ExpectedCollection<>(List.of("a"));
            factory.apply(expected);
            assertEquals(1, expected.isEmptyCalls);
            assertEquals(0, expected.sizeCalls);
            assertEquals(0, expected.iteratorCalls);
        }
    }

    @Test
    void throwingExpectedIsEmptyEscapesRawFromEveryElementsOfFactory() {
        List<Function<Collection<String>, ?>> factories = List.of(
                AwaitConditions::containsAllElementsOf,
                AwaitConditions::doesNotContainAllElementsOf,
                AwaitConditions::containsAnyElementsOf,
                AwaitConditions::containsNoElementsOf);

        for (Function<Collection<String>, ?> factory : factories) {
            RuntimeException cause = new IllegalStateException("isEmpty failed");
            var expected = new ProbeContainers.ExpectedCollection<String>(cause);
            assertSame(cause, assertThrows(RuntimeException.class,
                    () -> factory.apply(expected)));
            assertEquals(1, expected.isEmptyCalls);
            assertEquals(0, expected.sizeCalls);
            assertEquals(0, expected.iteratorCalls);
        }
    }

    @Test
    void everyPublicGenericVarargsFactoryIsSafeVarargs() {
        Set<String> membershipVarargs = Set.of(
                "containsAll", "doesNotContainAll",
                "containsAnyOf", "containsNoneOf");
        Set<String> discovered = Arrays.stream(
                        AwaitConditions.class.getDeclaredMethods())
                .filter(method -> method.isVarArgs()
                        && method.getTypeParameters().length > 0)
                .peek(method -> {
                    assertTrue(Modifier.isStatic(method.getModifiers()));
                    assertTrue(method.isAnnotationPresent(SafeVarargs.class),
                            method.getName());
                })
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(discovered.containsAll(membershipVarargs));
    }

    @Test
    void ordinaryConsumerCallsAreWarningFreeAndBareNullWarningRemains()
            throws IOException {
        assertTrue(CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.AwaitConditions.*;
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.*;
                import java.util.*;

                final class Contract {
                    void check() {
                        String nil = null;
                        Collection<String> values = List.of("a", "b");
                        Collection<Integer> integers = List.of(1, 2);
                        contains("a");
                        doesNotContain("a");
                        containsAll("a", "b");
                        doesNotContainAll("a", "b");
                        containsAnyOf("a", "b");
                        containsNoneOf("a", "b");
                        containsAll(nil);
                        containsAllElementsOf(values);
                        doesNotContainAllElementsOf(values);
                        containsAnyElementsOf(values);
                        containsNoElementsOf(values);
                        PreservingCondition<Collection<? super Integer>> typed =
                                containsAllElementsOf(integers);
                        AwaitSources.CollectionSource<Number, ArrayList<Number>>
                                source = () -> new ArrayList<>(integers);
                        ArrayList<Number> result = await(source)
                                .until(containsAllElementsOf(integers));
                    }
                }
                """));
        assertFalse(CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.AwaitConditions.*;
                final class Contract { void check() { containsAll(null); } }
                """));
        assertFalse(CompilationSupport.compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.AwaitConditions.*;
                import io.github.gromoff97.awium.*;
                import java.util.*;
                final class Contract {
                    void check(Collection<Object> broad,
                            AwaitSources.CollectionSource<String,
                                    ArrayList<String>> source) {
                        await(source).until(containsAllElementsOf(broad));
                    }
                }
                """));
    }

    private static void assertPair(Pair pair, List<String> values,
            boolean positiveSatisfied, int nextCalls, boolean explained)
            throws Exception {
        var positiveActual = new ProbeContainers.MembershipCollection<>(values);
        var negativeActual = new ProbeContainers.MembershipCollection<>(values);
        ConditionRuntime<ProbeContainers.MembershipCollection<String>,
                ProbeContainers.MembershipCollection<String>> positive =
                        runtime(pair.positive(), explained);
        ConditionRuntime<ProbeContainers.MembershipCollection<String>,
                ProbeContainers.MembershipCollection<String>> negative =
                        runtime(pair.negative(), explained);

        Evaluation<?> positiveEvaluation = positive.evaluate(positiveActual);
        Evaluation<?> negativeEvaluation = negative.evaluate(negativeActual);

        assertEquals(positiveSatisfied ? Evaluation.Status.SATISFIED
                        : Evaluation.Status.UNSATISFIED,
                positiveEvaluation.status(), pair.name());
        assertNotEquals(positiveEvaluation.status(), negativeEvaluation.status(),
                pair.name());
        assertEquals(explained ? "reason" : null, positive.explanation());
        assertEquals(explained ? "reason" : null, negative.explanation());
        assertFalse(positive.description().get().isBlank());
        assertFalse(negative.description().get().isBlank());
        assertOnlyIterator(positiveActual, nextCalls);
        assertOnlyIterator(negativeActual, nextCalls);
    }

    private static ConditionRuntime<ProbeContainers.MembershipCollection<String>,
            ProbeContainers.MembershipCollection<String>> runtime(
                    PreservingCondition<? super ProbeContainers.
                            MembershipCollection<String>> condition,
                    boolean explained) {
        return explained
                ? ConditionAdapters.preserving(condition.because("reason"))
                : ConditionAdapters.preserving(condition);
    }

    private static void assertNullActual(
            ConditionRuntime<ProbeContainers.MembershipCollection<String>,
                    ProbeContainers.MembershipCollection<String>> runtime)
            throws Exception {
        Evaluation<?> evaluation = runtime.evaluate(null);
        assertEquals(Evaluation.Status.UNSATISFIED, evaluation.status());
        assertEquals("collection was null", evaluation.mismatch());
    }

    private static void assertOnlyIterator(
            ProbeContainers.MembershipCollection<?> actual, int nextCalls) {
        assertEquals(0, actual.sizeCalls);
        assertEquals(0, actual.isEmptyCalls);
        assertEquals(1, actual.iteratorCalls);
        assertEquals(nextCalls, actual.nextCalls);
        assertEquals(0, actual.containsCalls);
        assertEquals(0, actual.containsAllCalls);
        assertEquals(0, actual.equalsCalls);
        assertEquals(0, actual.hashCodeCalls);
    }

    private static void assertSatisfied(Evaluation<?> evaluation,
            Object actual) {
        assertEquals(Evaluation.Status.SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static <T extends Throwable> void assertFailure(Executable executable,
            Class<T> type, String message) {
        assertEquals(message, assertThrows(type, executable).getMessage());
    }

    private static CollectionUntil<String,
            ProbeContainers.MembershipCollection<String>> timedCollection(
                    ProbeContainers.MembershipCollection<String> actual) {
        FakeTime time = new FakeTime(0);
        AwaitChain<ProbeContainers.MembershipCollection<String>> chain =
                new AwaitChain<>(() -> {
                    time.advanceNanos(2);
                    return actual;
                }, WaitConfiguration.defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(2)), time, time,
                new Interrupts(), new FailureFactory());
        return new CollectionStageAdapters.CollectionAfterUpToStage<>(chain);
    }

    private static <E> ProbeContainers.MembershipCollection<E> await(
            ProbeContainers.MembershipCollection<E> actual,
            PreservingCondition<? super ProbeContainers.MembershipCollection<E>>
                    condition) {
        return Awium.await((AwaitSources.CollectionSource<E,
                ProbeContainers.MembershipCollection<E>>) () -> actual)
                .until(condition);
    }

    private record Pair(String name,
            PreservingCondition<Collection<? super String>> positive,
            PreservingCondition<Collection<? super String>> negative,
            List<String> matchingActual, List<String> mismatchingActual,
            int matchingNextCalls, int mismatchingNextCalls) {
    }

    private static final class Directional {
        private final boolean equalsResult;
        private int equalsCalls;

        private Directional(boolean equalsResult) {
            this.equalsResult = equalsResult;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof Directional && equalsResult;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    private static final class CountingValue {
        private final String value;
        private int equalsCalls;

        private CountingValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof CountingValue expected
                    && value.equals(expected.value);
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
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
