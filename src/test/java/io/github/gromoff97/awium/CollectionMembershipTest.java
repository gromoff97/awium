package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.preserving;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedStructuralAwait;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CollectionMembershipTest {

    private static final List<Pair> PAIRS = List.of(
            new Pair("contains", contains("b"),
                    doesNotContain("b")),
            new Pair("containsAll", containsAll("a", "b"),
                    doesNotContainAll("a", "b")),
            new Pair("containsAllElementsOf",
                    containsAllElementsOf(List.of("a", "b")),
                    doesNotContainAllElementsOf(
                            List.of("a", "b"))),
            new Pair("containsAnyOf", containsAnyOf("x", "b"),
                    containsNoneOf("x", "b")),
            new Pair("containsAnyElementsOf",
                    containsAnyElementsOf(List.of("x", "b")),
                    containsNoElementsOf(List.of("x", "b"))));

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeRawMembershipTableIsComplementary()
            throws Exception {
        for (Pair pair : PAIRS) {
            assertPair(pair, List.of("a", "b", "c"), true, 2);
            assertPair(pair, List.of("a", "c"), false, 2);
        }
    }

    @Test
    void nullActualIsUnsatisfiedBeforeMembership() throws Exception {
        assertNullActual(runtime(contains("b")));
    }

    @Test
    void repeatedExpectedPositionsAreSetLike() throws Exception {
        assertPair(new Pair("containsAll", containsAll("a", "a"),
                doesNotContainAll("a", "a")), List.of("a"), true, 1);
    }

    @Test
    void valueEqualityIsActualFirstArrayAwareAndNeverHashes() throws Exception {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);
        var directional = new ProbeContainers.MembershipCollection<>(
                List.of(actual));
        var arrays = new ProbeContainers.MembershipCollection<Object>(
                List.of(new int[] {1, 2}));

        assertSatisfied(RuntimeCondition.<
                ProbeContainers.MembershipCollection<Directional>>preserving(
                        contains(expected)).evaluate(directional),
                directional);
        assertSatisfied(RuntimeCondition.<
                ProbeContainers.MembershipCollection<Object>>preserving(
                        contains(new int[] {1, 2})).evaluate(arrays),
                arrays);

        assertEquals(1, actual.equalsCalls);
        assertOnlyIterator(directional, 1);
        assertOnlyIterator(arrays, 1);
    }

    @Test
    void terminalDiagnosticsDoNotTraverseAgain() {
        var explained = new ProbeContainers.MembershipCollection<>(
                List.of("a", "b"));
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedStructuralAwait(
                        (Source<ProbeContainers.
                                MembershipCollection<String>>) () -> {
                            time.advanceNanos(2);
                            return explained;
                        }, "collection", Collection::size,
                        defaults().withEvery(ofNanos(1))
                                .withUpTo(ofNanos(2)), time, time)
                        .until(doesNotContain("a").because("required")));

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
                        doesNotContain("a"))).getCause());
        assertSame(advancementCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(advancement,
                        containsNoneOf("a"))).getCause());
        assertSame(equalityCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(equality,
                        doesNotContain(
                                new ThrowingEquals(null)))).getCause());
    }

    @Test
    void aggregateFactoriesRejectNullAndEmptyInputsWithExactMessages() {
        assertFailure(() -> containsAll((Object[]) null),
                NullPointerException.class,
                "expected elements must not be null");
        assertFailure(() -> containsAllElementsOf(
                        (Collection<Object>) null),
                NullPointerException.class,
                "expected elements must not be null");
        assertFailure(() -> containsAll(new Object[0]),
                IllegalArgumentException.class,
                "expected elements must not be empty");
        assertFailure(() -> containsAllElementsOf(List.of()),
                IllegalArgumentException.class,
                "expected elements must not be empty");
    }

    @Test
    void typedNullIsOneValidExpectedElement() throws Exception {
        String expected = null;
        var actual = new ProbeContainers.MembershipCollection<String>(
                asList((String) null));

        assertSatisfied(runtime(containsAll(expected))
                .evaluate(actual), actual);
        assertOnlyIterator(actual, 1);
    }

    @Test
    void elementsOfFactoryCallsOnlyIsEmptyOnceAtConstruction() {
        var expected = new ProbeContainers.ExpectedCollection<String>();
        containsAllElementsOf(expected);
        assertEquals(1, expected.isEmptyCalls);
    }

    @Test
    void ordinaryConsumerCallsAreWarningFreeAndBareNullWarningRemains()
            throws IOException {
        assertTrue(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.sources.CollectionSource;
                import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
                import java.util.*;

                final class Contract {
                    void check() {
                        String nil = null;
                        Collection<Integer> integers = List.of(1, 2);
                        containsAll("a", "b");
                        doesNotContainAll("a", "b");
                        containsAnyOf("a", "b");
                        containsNoneOf("a", "b");
                        containsAll(nil);
                        PreservingCondition<Collection<? super Integer>> typed =
                                containsAllElementsOf(integers);
                        CollectionSource<ArrayList<Number>> source =
                                () -> new ArrayList<>(integers);
                        ArrayList<Number> result = await(source)
                                .until(containsAllElementsOf(integers));
                    }
                }
                """));
        assertFalse(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                final class Contract { void check() { containsAll(null); } }
                """));
        assertFalse(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                import io.github.gromoff97.awium.sources.CollectionSource;
                import java.util.*;
                final class Contract {
                    void check(Collection<Object> broad,
                            CollectionSource<ArrayList<String>> source) {
                        await(source).until(containsAllElementsOf(broad));
                    }
                }
                """));
    }

    private static void assertPair(Pair pair, List<String> values,
            boolean positiveSatisfied, int nextCalls) throws Exception {
        var positiveActual = new ProbeContainers.MembershipCollection<>(values);
        var negativeActual = new ProbeContainers.MembershipCollection<>(values);
        RuntimeCondition<ProbeContainers.MembershipCollection<String>,
                ProbeContainers.MembershipCollection<String>> positive =
                        runtime(pair.positive());
        RuntimeCondition<ProbeContainers.MembershipCollection<String>,
                ProbeContainers.MembershipCollection<String>> negative =
                        runtime(pair.negative());

        Evaluation<?> positiveEvaluation = positive.evaluate(positiveActual);
        Evaluation<?> negativeEvaluation = negative.evaluate(negativeActual);

        assertEquals(positiveSatisfied ? SATISFIED
                        : UNSATISFIED,
                positiveEvaluation.status(), pair.name());
        assertNotEquals(positiveEvaluation.status(), negativeEvaluation.status(),
                pair.name());
        assertNull(positive.explanation());
        assertNull(negative.explanation());
        assertFalse(positive.description().get().isBlank());
        assertFalse(negative.description().get().isBlank());
        assertOnlyIterator(positiveActual, nextCalls);
        assertOnlyIterator(negativeActual, nextCalls);
    }

    private static RuntimeCondition<ProbeContainers.MembershipCollection<String>,
            ProbeContainers.MembershipCollection<String>> runtime(
                    PreservingCondition<? super ProbeContainers.
                            MembershipCollection<String>> condition) {
        return preserving(condition);
    }

    private static void assertNullActual(
            RuntimeCondition<ProbeContainers.MembershipCollection<String>,
                    ProbeContainers.MembershipCollection<String>> runtime)
            throws Exception {
        Evaluation<?> evaluation = runtime.evaluate(null);
        assertEquals(UNSATISFIED, evaluation.status());
        assertEquals("collection was null", evaluation.mismatch());
    }

    private static void assertOnlyIterator(
            ProbeContainers.MembershipCollection<?> actual, int nextCalls) {
        assertEquals(1, actual.iteratorCalls);
        assertEquals(nextCalls, actual.nextCalls);
    }

    private static void assertSatisfied(Evaluation<?> evaluation,
            Object actual) {
        assertEquals(SATISFIED, evaluation.status());
        assertSame(actual, evaluation.result());
        assertNull(evaluation.mismatch());
    }

    private static <T extends Throwable> void assertFailure(Executable executable,
            Class<T> type, String message) {
        assertEquals(message, assertThrows(type, executable).getMessage());
    }

    private static <E> ProbeContainers.MembershipCollection<E> await(
            ProbeContainers.MembershipCollection<E> actual,
            PreservingCondition<? super ProbeContainers.MembershipCollection<E>>
                    condition) {
        return Awium.await((CollectionSource<
                ProbeContainers.MembershipCollection<E>>) () -> actual)
                .until(condition);
    }

    private record Pair(String name,
            PreservingCondition<Collection<? super String>> positive,
            PreservingCondition<Collection<? super String>> negative) {
    }

}
