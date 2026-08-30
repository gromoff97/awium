package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Await;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import static io.github.gromoff97.awium.CompilationSupport.compiles;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.internal.condition.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.fluent.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.*;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
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
        assertUnsatisfied(evaluate(contains("b"), null));
        assertPair(new Pair("repeated", contains("a", "a"),
                doesNotContainAll("a", "a")), List.of("a"), true);

        String nil = null;
        Collection<String> actual = asList((String) null);
        assertSame(actual, result(evaluate(contains(nil), actual)));
    }

    @Test
    void membershipUsesActualFirstArrayAwareEqualityWithoutHashing()
            throws Exception {
        Directional actualValue = new Directional(true);
        Directional expectedValue = new Directional(false);
        Collection<Object> directional = new ArrayList<>(List.of(actualValue));
        Collection<Object> arrays = new ArrayList<>(
                List.of(new int[] {1, 2}));

        assertSame(directional, result(evaluate(contains(expectedValue), directional)));
        assertSame(arrays, result(evaluate(contains(new int[] {1, 2}), arrays)));
        assertEquals(1, actualValue.equalsCalls);
    }

    @Test
    void clearedLiveExpectedCollectionUsesEmptyContainmentSemantics()
            throws Exception {
        var expected = new ArrayList<>(List.of("expected"));
        var positive = containsAll(expected);
        var negative = doesNotContainAllElementsOf(expected);
        expected.clear();

        for (Collection<String> actual : List.of(List.<String>of(), List.of("actual"))) {
            assertEquals(SATISFIED, evaluate(positive, actual).status());
            assertEquals(UNSATISFIED, evaluate(negative, actual).status());
        }
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
                        doesNotContain(new ThrowingEquals(null)))).getCause());
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
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(doesNotContain("a").because("business reason")));

        assertEquals(1, actual.iteratorCalls);
    }

    @Test
    void aggregateFactoriesRejectNullAndEmptyInputs() {
        assertValidation(NullPointerException.class,
                () -> contains((Object[]) null));
        assertValidation(NullPointerException.class,
                () -> containsAll((Collection<Object>) null));
        assertValidation(IllegalArgumentException.class,
                () -> contains(new Object[0]));
        assertValidation(IllegalArgumentException.class,
                () -> containsAll(List.of()));
    }

    @Test
    void ordinaryConsumerCallsAreWarningFree()
            throws IOException {
        assertTrue(compiles(temporaryDirectory, """
                import static io.github.gromoff97.awium.fluent.Await.await;
                import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
                import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check() {
                        String nil = null;
                        Collection<Integer> integers = List.of(1, 2);
                        contains("a", "b");
                        doesNotContainAll("a", "b");
                        containsAnyOf("a", "b");
                        doesNotContain("a", "b");
                        contains(nil);
                        PreservingCondition<Collection<? super Integer>> typed =
                                containsAll(integers);
                        CollectionSource<ArrayList<Number>> source =
                                () -> new ArrayList<>(integers);
                        ArrayList<Number> result = await(source).until(containsAll(integers));
                    }
                }
                """));
    }

    private static void assertPair(Pair pair, List<String> values,
            boolean positiveSatisfied) throws Exception {
        Collection<String> actual = new ArrayList<>(values);
        ConditionEvaluation<?> positive = evaluate(pair.positive(), actual);
        ConditionEvaluation<?> negative = evaluate(pair.negative(), actual);

        assertEquals(positiveSatisfied ? SATISFIED : UNSATISFIED,
                positive.status(), pair.name());
        assertNotEquals(positive.status(), negative.status(), pair.name());
        assertSame(actual, result(positiveSatisfied ? positive : negative));
        assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank());
    }

    private static void assertUnsatisfied(ConditionEvaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertInstanceOf(ConditionEvaluation.Unsatisfied.class, evaluation);
        assertFalse(mismatch(evaluation).isBlank());
    }

    private static void assertValidation(Class<? extends Throwable> type,
            org.junit.jupiter.api.function.Executable action) {
        assertTrue(!assertThrows(type, action).getMessage().isBlank());
    }

    private static <E> ProbeContainers.MembershipCollection<E> await(
            ProbeContainers.MembershipCollection<E> actual,
            PreservingCondition<? super ProbeContainers.MembershipCollection<E>>
                    condition) {
        return Await.await((CollectionSource<ProbeContainers.MembershipCollection<E>>)
                () -> actual).until(condition);
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("contains", contains("b"), doesNotContain("b")),
                new Pair("contains", contains("a", "b"),
                        doesNotContainAll("a", "b")),
                new Pair("contains collection", containsAll(List.of("a", "b")),
                        doesNotContainAllElementsOf(List.of("a", "b"))),
                new Pair("containsAny", containsAnyOf("x", "b"),
                        doesNotContain("x", "b")),
                new Pair("containsAny collection", containsAnyElementsOf(List.of("x", "b")),
                        doesNotContainAnyElementsOf(List.of("x", "b"))));
    }

    private record Pair(String name,
            PreservingCondition<Collection<? super String>> positive,
            PreservingCondition<Collection<? super String>> negative) {}
}
