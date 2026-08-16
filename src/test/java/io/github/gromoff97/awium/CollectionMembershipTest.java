package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.*;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectionMembershipTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void positiveAndNegativeMembershipConditionsAreComplements()
            throws Exception {
        for (Pair pair : pairs()) {
            assertPair(pair, List.of("a", "b", "c"), true);
            assertPair(pair, List.of("a", "c"), false);
        }
    }

    @Test
    void nullAndRepeatedExpectedValuesKeepMembershipSemantics()
            throws Exception {
        assertUnsatisfied(contains("b").delegate().evaluate(null));
        assertPair(new Pair("repeated", containsAll("a", "a"),
                doesNotContainAll("a", "a")), List.of("a"), true);

        String nil = null;
        Collection<String> actual = asList((String) null);
        assertSame(actual, containsAll(nil).delegate().evaluate(actual).result());
    }

    @Test
    void membershipUsesActualFirstArrayAwareEqualityWithoutHashing()
            throws Exception {
        Directional actualValue = new Directional(true);
        Directional expectedValue = new Directional(false);
        Collection<Object> directional = new ArrayList<>(List.of(actualValue));
        Collection<Object> arrays = new ArrayList<>(
                List.of(new int[] {1, 2}));

        assertSame(directional, contains(expectedValue).delegate().evaluate(directional).result());
        assertSame(arrays, contains(new int[] {1, 2}).delegate().evaluate(arrays).result());
        assertEquals(1, actualValue.equalsCalls);
    }

    @Test
    void traversalAndEqualityFailuresRemainFailFastForNegativeConditions() {
        var iteratorCause = new IllegalStateException("iterator failed");
        var equalityCause = new IllegalStateException("equals failed");
        var brokenIterator = new ProbeContainers.MembershipCollection<String>(
                iteratorCause);
        var brokenEquality = new ProbeContainers.MembershipCollection<>(
                List.of(new ThrowingEquals(equalityCause)));

        assertSame(iteratorCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(brokenIterator, doesNotContain("a"))).getCause());
        assertSame(equalityCause, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> await(brokenEquality,
                        containsNoneOf(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = new ProbeContainers.MembershipCollection<>(
                List.of("a", "b"));
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait(
                        (Source<ProbeContainers.MembershipCollection<String>>)
                                () -> {
                                    time.advanceNanos(2);
                                    return actual;
                                },
                        defaults().withEvery(ofNanos(1))
                                .withUpTo(ofNanos(2)), time, time)
                        .until(doesNotContain("a").because("business reason")));

        assertEquals(1, actual.iteratorCalls);
    }

    @Test
    void aggregateFactoriesRejectNullAndEmptyInputs() {
        assertValidation(NullPointerException.class,
                () -> containsAll((Object[]) null));
        assertValidation(NullPointerException.class,
                () -> containsAllElementsOf((Collection<Object>) null));
        assertValidation(IllegalArgumentException.class,
                () -> containsAll(new Object[0]));
        assertValidation(IllegalArgumentException.class,
                () -> containsAllElementsOf(List.of()));
    }

    @Test
    void ordinaryConsumerCallsAreWarningFreeAndBareNullRemainsAmbiguous()
            throws IOException {
        assertTrue(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
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
    }

    private static void assertPair(Pair pair, List<String> values,
            boolean positiveSatisfied) throws Exception {
        Collection<String> actual = new ArrayList<>(values);
        Evaluation<?> positive = pair.positive().delegate().evaluate(actual);
        Evaluation<?> negative = pair.negative().delegate().evaluate(actual);

        assertEquals(positiveSatisfied ? SATISFIED : UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertSame(actual, (positiveSatisfied ? positive : negative).result());
        assertFalse((positiveSatisfied ? negative : positive)
                .mismatch().isBlank());
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertFalse(evaluation.mismatch().isBlank());
    }

    private static void assertValidation(Class<? extends Throwable> type,
            org.junit.jupiter.api.function.Executable action) {
        assertTrue(!assertThrows(type, action).getMessage().isBlank());
    }

    private static <E> ProbeContainers.MembershipCollection<E> await(
            ProbeContainers.MembershipCollection<E> actual,
            PreservingCondition<? super ProbeContainers.MembershipCollection<E>>
                    condition) {
        return Await.await((CollectionSource<
                ProbeContainers.MembershipCollection<E>>) () -> actual)
                .until(condition);
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("contains", contains("b"), doesNotContain("b")),
                new Pair("containsAll", containsAll("a", "b"),
                        doesNotContainAll("a", "b")),
                new Pair("containsAllElementsOf",
                        containsAllElementsOf(List.of("a", "b")),
                        doesNotContainAllElementsOf(List.of("a", "b"))),
                new Pair("containsAnyOf", containsAnyOf("x", "b"),
                        containsNoneOf("x", "b")),
                new Pair("containsAnyElementsOf",
                        containsAnyElementsOf(List.of("x", "b")),
                        containsNoElementsOf(List.of("x", "b"))));
    }

    private record Pair(String name,
            PreservingCondition<Collection<? super String>> positive,
            PreservingCondition<Collection<? super String>> negative) {}
}
