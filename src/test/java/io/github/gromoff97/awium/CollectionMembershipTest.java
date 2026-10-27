package io.github.gromoff97.awium;


import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static java.util.Arrays.asList;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static java.time.Duration.ofNanos;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

class CollectionMembershipTest {

    @Test
    void positiveAndNegativeMembershipConditionsAreComplements() {
        for (Pair pair : pairs()) {
            assertPair(pair, List.of("a", "b", "c"), true);
            assertPair(pair, List.of("a", "c"), false);
        }
    }

    @Test
    void containmentAgreesWithMembershipAcrossNullsDuplicatesAndMissingValues() {
        var inputs = new ArrayList<List<String>>();
        inputs.add(new ArrayList<>());
        List<String> alphabet = asList(null, "a", "b");
        for (int length = 1; length <= 3; length++) {
            int size = length;
            var prefixes = inputs.stream().filter(value -> value.size() == size - 1).toList();
            for (var prefix : prefixes) {
                for (String value : alphabet) {
                    var next = new ArrayList<>(prefix);
                    next.add(value);
                    inputs.add(next);
                }
            }
        }
        for (var expected : inputs) {
            var condition = containsAll(expected);
            for (var actual : inputs) {
                assertEquals(actual.containsAll(expected), evaluate(condition, actual) instanceof Satisfied<?>);
            }
        }
    }

    @Test
    void containmentSearchesForEachExpectedValueUsingActualFirstEquality() {
        record Value(String name, List<String> calls) {
            @Override
            public boolean equals(Object other) {
                if (!(other instanceof Value value)) return false;
                calls.add(name + "->" + value.name);
                return name.equals(value.name);
            }

            @Override
            public int hashCode() { throw new AssertionError("membership must not hash values"); }
        }
        var comparisons = new ArrayList<String>();
        var actual = List.of(new Value("a", comparisons), new Value("b", comparisons));
        var expected = List.of(new Value("b", List.of()), new Value("a", List.of()));
        assertSame(actual, await(() -> actual).until(containsAll(expected)));
        assertEquals(List.of("a->b", "b->b", "a->a"), comparisons);
    }

    @Test
    void nullAndRepeatedExpectedValuesKeepMembershipSemantics() {
        assertUnsatisfied(evaluate(contains("b"), null));
        assertPair(new Pair("repeated", containsAll(List.of("a", "a")),
                doesNotContainAll(List.of("a", "a"))), List.of("a"), true);

        String nil = null;
        Collection<String> actual = asList((String) null);
        assertSame(actual, result(evaluate(contains(nil), actual)));
    }

    @Test
    void membershipUsesActualFirstArrayAwareEqualityWithoutHashing() {
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
    void collectionArgumentsRepresentExpectedElementsIncludingNestedCollections() {
        List<String> element = List.of("a", "b");
        Collection<List<String>> actual = List.of(element);

        assertSame(actual, result(evaluate(containsAll(List.of(element)), actual)));
        assertUnsatisfied(evaluate(containsAll(List.of(List.of("a"))), actual));
    }

    @Test
    void listsAndReferenceArraysAreSingleElementsInFluentCalls() {
        var time = new FakeTime(0);
        var element = List.of("a", "b");
        var lists = List.of(element);
        List<List<String>> selectedList = await(() -> lists).usingTime(time, time)
                .until(contains(element));
        assertSame(lists, selectedList);
        assertEquals(1, selectedList.getFirst().getFirst().length());

        String[] array = {"a", "b"};
        var arrays = singletonList(array);
        List<String[]> selectedArray = await(() -> arrays).usingTime(time, time)
                .until(contains(array));
        assertSame(arrays, selectedArray);
        assertEquals(2, selectedArray.getFirst().length);
        List<String[]> onlyArray = await(() -> arrays).usingTime(time, time)
                .until(containsOnly(singletonList(array)));
        assertSame(arrays, onlyArray);

        var otherLists = List.of(List.of("c"));
        assertSame(otherLists, await(() -> otherLists).usingTime(time, time)
                .until(doesNotContain(element)));
        var otherArrays = singletonList(new String[] {"c"});
        assertSame(otherArrays, await(() -> otherArrays).usingTime(time, time)
                .until(doesNotContain(array)));
    }

    @Test
    void singleContainerOperandsCanBeEmptyOrNullAndRemainCallerOwned() {
        var time = new FakeTime(0);
        var expected = new ArrayList<String>();
        var actual = singletonList(new ArrayList<String>());
        var condition = contains(expected);
        assertSame(actual, await(() -> actual).usingTime(time, time).until(condition));
        expected.add("changed");
        actual.getFirst().add("changed");
        assertSame(actual, await(() -> actual).usingTime(time, time).until(condition));

        String[] empty = {};
        var emptyArrays = singletonList(empty);
        assertSame(emptyArrays, await(() -> emptyArrays).usingTime(time, time).until(contains(empty)));
        String[] missing = null;
        var nullArrays = singletonList(missing);
        assertSame(nullArrays, await(() -> nullArrays).usingTime(time, time).until(contains(missing)));
    }

    @Test
    void clearingExpectedCollectionDoesNotChangeContainment() {
        var expected = new ArrayList<>(List.of("expected"));
        var positive = containsAll(expected);
        var negative = doesNotContainAll(expected);
        expected.clear();

        for (Collection<String> actual : List.of(List.<String>of(), List.of("actual"))) {
            assertEquals(Unsatisfied.class, evaluate(positive, actual).getClass());
            assertEquals(Satisfied.class, evaluate(negative, actual).getClass());
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

        assertSame(iteratorCause, assertFailure(CONDITION_FAILED,
                () -> checkMembership(brokenIterator, doesNotContain("a"))).getCause());
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> checkMembership(brokenEquality,
                        doesNotContain(new ThrowingEquals(null)))).getCause());
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = new ProbeContainers.MembershipCollection<>(
                List.of("a", "b"));
        FakeTime time = new FakeTime(0);

        assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(doesNotContain("a")));

        assertEquals(1, actual.iteratorCalls);
    }

    @Test
    void aggregateFactoriesRejectNullInputs() {
        assertValidation(NullPointerException.class,
                () -> doesNotContainAny(null));
        assertValidation(NullPointerException.class,
                () -> containsAll(null));
        assertValidation(NullPointerException.class,
                () -> containsAny(null));
        assertValidation(NullPointerException.class,
                () -> doesNotContainAll(null));
    }

    @Test
    void emptyExpectedSetsFollowMembershipSemanticsAcrossTerminals() {
        List<PreservingCondition<Collection<? super String>>> checks = List.of(
                containsAll(List.of()), doesNotContainAny(List.of()),
                containsAny(List.of()), doesNotContainAll(List.of()));
        for (var check : checks) assertUnsatisfied(evaluate(check, null));

        for (var values : List.of(List.<String>of(), List.of("actual"))) {
            var actual = new ArrayList<>(values);
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

            ArrayList<String> all = waiting.until(containsAll(List.of()));
            ArrayList<String> absent = waiting.until(doesNotContainAny(List.of()));
            assertSame(actual, all);
            assertSame(actual, absent);
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class,
                    waiting.tryUntil(containsAll(List.of()))).result());

            for (var failed : List.of(waiting.tryUntil(containsAny(List.of())),
                    waiting.tryUntil(doesNotContainAll(List.of())))) {
                assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, failed).failure().reason());
                assertEquals(List.of(), failed.attempts().getLast().condition().reference().value());
            }
            assertEquals(actual.isEmpty() ? Satisfied.class : Unsatisfied.class,
                    evaluate(containsExactly(List.<String>of()), actual).getClass());
        }
    }

    @Test
    void aggregateConditionsPreserveTheConcreteCollectionType() {
        Collection<Integer> integers = List.of(1, 2);
        var actual = new ArrayList<Number>(integers);
        java.util.concurrent.Callable<ArrayList<Number>> source = () -> actual;
        PreservingCondition<Collection<? super Integer>> typed = containsAll(integers);

        ArrayList<Number> result = await(source::call).until(typed);

        assertSame(actual, result);
    }

    private static void assertPair(Pair pair, List<String> values,
            boolean positiveSatisfied) {
        Collection<String> actual = new ArrayList<>(values);
        ConditionResult<?> positive = evaluate(pair.positive(), actual);
        ConditionResult<?> negative = evaluate(pair.negative(), actual);

        assertAll(pair.name(),
                () -> assertSame(actual, result(positiveSatisfied ? positive : negative)),
                () -> assertFalse(mismatch(positiveSatisfied ? negative : positive).isBlank()));
    }

    private static void assertValidation(Class<? extends Throwable> type,
            org.junit.jupiter.api.function.Executable action) {
        assertFalse(assertThrows(type, action).getMessage().isBlank());
    }

    private static <E> ProbeContainers.MembershipCollection<E> checkMembership(
            ProbeContainers.MembershipCollection<E> actual,
            PreservingCondition<? super ProbeContainers.MembershipCollection<E>>
                    condition) {
        var time = new FakeTime(0);
        return await(() -> actual).usingTime(time, time).until(condition);
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair("contains", contains("b"), doesNotContain("b")),
                new Pair("containsAll", containsAll(List.of("a", "b")),
                        doesNotContainAll(List.of("a", "b"))),
                new Pair("containsAny", containsAny(List.of("x", "b")),
                        doesNotContainAny(List.of("x", "b"))));
    }

    private record Pair(String name,
            PreservingCondition<Collection<? super String>> positive,
            PreservingCondition<Collection<? super String>> negative) {}
}
