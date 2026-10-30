package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ExpectedInputsTest {

    private final FakeTime time = new FakeTime(0);

    @ParameterizedTest(name = "negative={0}")
    @ValueSource(booleans = {false, true})
    void collectionFailuresRetainTheirExpectedAndUnexpectedElements(boolean negative) {
        var operands = List.of("a", "b");
        List<PreservingCondition<? super List<String>>> checks = negative
                ? List.of(doesNotContainExactly(operands), doesNotContainExactlyInAnyOrder(operands))
                : List.of(containsExactlyInAnyOrder(operands), containsOnly(operands), subsetOf(operands));
        var actual = negative ? operands : List.of("other");
        for (var check : checks) {
            var time = new FakeTime(0);
            var result = tryAwait(() -> actual).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
            String operand = (negative ? "Unexpected" : "Expected") + ": [a, b]";
            assertThat(failure(result).message()).contains(operand);
            assertThat(result.conditions().getFirst().toString()).contains(operand);
        }
    }

    @ParameterizedTest(name = "negative={0}")
    @ValueSource(booleans = {false, true})
    void mapEntryFailuresRetainBothOperands(boolean negative) {
        var check = negative ? MapConditions.hasNoEntry("key", "value") : MapConditions.hasEntry("key", "value");
        var actual = negative ? Map.of("key", "value") : Map.of("other", "value");
        var result = tryAwait(() -> actual).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
        String operand = (negative ? "Unexpected" : "Expected") + ": key=value";
        assertThat(failure(result).message()).contains(operand);
        assertThat(result.conditions().getFirst().toString()).contains(operand);
    }

    @Test
    void mapKeyFailuresIdentifyTheMissingKey() {
        var waiting = tryAwait(() -> Map.of("other", "value")).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1));
        var keys = waiting.until(MapConditions.containsOnlyKeys(List.of("key")));
        var entry = waiting.until(MapConditions.entryFor("key"));

        assertThat(failure(keys).message()).contains("Expected: [key]");
        assertThat(keys.conditions().getFirst().toString()).contains("Expected: [key]");
        assertThat(failure(entry).message()).contains("Key: \"key\"");
        assertThat(entry.conditions().getFirst().toString()).contains("Key: \"key\"");
    }

    @ParameterizedTest(name = "negative={0}")
    @ValueSource(booleans = {false, true})
    void stringFailuresRetainTheExpectedText(boolean negative) {
        var checks = negative
                ? List.of(StringConditions.doesNotContain("needle"), StringConditions.doesNotStartWith("needle"),
                        StringConditions.doesNotEndWith("needle"), StringConditions.notEqualToIgnoringCase("needle"))
                : List.of(StringConditions.contains("needle"), StringConditions.containsIgnoringCase("needle"),
                        StringConditions.startsWith("needle"), StringConditions.endsWith("needle"),
                        StringConditions.contentEquals("needle"), StringConditions.equalToIgnoringCase("needle"));
        for (var check : checks) {
            var time = new FakeTime(0);
            var result = tryAwait(() -> negative ? "needle" : "other").usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
            String operand = (negative ? "Unexpected" : "Expected") + ": \"needle\"";
            assertThat(failure(result).message()).contains(operand);
            assertThat(result.conditions().getFirst().toString()).contains(operand);
        }
    }

    @ParameterizedTest(name = "negative={0}")
    @ValueSource(booleans = {false, true})
    void regexFailuresRetainThePattern(boolean negative) {
        var check = negative ? StringConditions.doesNotMatchRegex("a.*z") : StringConditions.matchesRegex("a.*z");
        var result = tryAwait(() -> negative ? "abz" : "other").usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
        String operand = (negative ? "Unexpected" : "Expected") + ": a.*z";
        assertThat(failure(result).message()).contains(operand);
        assertThat(result.conditions().getFirst().toString()).contains(operand);
    }

    @Test
    void comparisonFailuresRetainTheirOperandsAndBounds() {
        for (boolean negative : List.of(false, true)) {
            var operand = new String("needle");
            var checks = negative ? List.of(Conditions.notEqualTo(operand), Conditions.notSameAs(operand))
                    : List.of(Conditions.equalTo(operand), Conditions.sameAs(operand));
            for (var check : checks) {
                var time = new FakeTime(0);
                var result = tryAwait(() -> negative ? operand : "other").usingTime(time, time)
                        .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
                assertThat(failure(result).message()).contains((negative ? "Unexpected" : "Expected") + ": \"needle\"");
            }
        }
        List<PreservingCondition<Integer>> ranges = List.of(Conditions.between(1, 5), Conditions.strictlyBetween(1, 5));
        for (var check : ranges) {
            var time = new FakeTime(0);
            var result = tryAwait(() -> 10).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(1)).until(check);
            assertThat(failure(result).message()).contains("Expected: [1, 5]");
        }
        var time = new FakeTime(0);
        var result = tryAwait(() -> 0).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).until(Conditions.greaterThan(5));
        assertThat(failure(result).message()).contains("Expected: 5");
    }

    @Test
    void singleOperandsRemainWholeValuesInDiagnostics() {
        int[] operand = {1, 2};
        List<Condition> conditions = List.of(contains(operand), doesNotContain(operand),
                MapConditions.containsKey(operand), MapConditions.doesNotContainKey(operand),
                MapConditions.containsValue(operand), MapConditions.doesNotContainValue(operand));
        for (var condition : conditions) {
            assertSame(operand, condition.references().getFirst().value());
        }
        for (boolean negative : List.of(false, true)) {
            var actual = List.of(negative ? operand : new int[] {9});
            var condition = negative ? doesNotContain(operand) : contains(operand);
            var time = new FakeTime(0);
            var outcome = tryAwait(() -> actual).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(1))
                    .until(condition);
            assertThat(failure(outcome).message()).contains((negative ? "Unexpected" : "Expected") + ": [1, 2]");
        }
    }

    @Test
    void validationUsesTheSnapshotInsteadOfTheOriginalContainersEmptinessQuery() {
        var expected = new ArrayList<>(List.of("before")) {
            @Override
            public boolean isEmpty() {
                boolean empty = super.isEmpty();
                clear();
                return empty;
            }
        };
        var condition = containsAll(expected);
        assertEquals(List.of("before"), condition.references().getFirst().value());
        expected.clear();
        assertInstanceOf(Unsatisfied.class, evaluate(condition, List.of("unrelated")));
        assertInstanceOf(Unsatisfied.class, evaluate(condition, null));
    }

    @Test
    void factoriesKeepEmptySnapshotsAfterRemovalBeforeSnapshot() {
        List<Function<Collection<String>, Condition>> collections = List.of(
                CollectionConditions::containsAll, CollectionConditions::doesNotContainAny,
                CollectionConditions::doesNotContainAll, CollectionConditions::containsAny,
                CollectionConditions::startsWith, CollectionConditions::endsWith,
                CollectionConditions::containsSequence, CollectionConditions::doesNotContainSequence,
                CollectionConditions::containsSubsequence, CollectionConditions::doesNotContainSubsequence,
                Conditions::in, Conditions::notIn,
                StringConditions::containsAll, StringConditions::doesNotContainAny,
                MapConditions::containsAllKeys, MapConditions::doesNotContainAnyKeys,
                MapConditions::containsAllValues, MapConditions::doesNotContainAnyValues);
        for (var factory : collections) {
            var expected = new ChangingExpectedQueue();
            expected.add("before");
            var condition = factory.apply(expected);
            assertEquals(List.of(), condition.references().getFirst().value());
            expected.add("after");
            assertEquals(List.of(), condition.references().getFirst().value());
        }

        List<Function<Map<String, String>, PreservingCondition<?>>> maps = List.of(
                MapConditions::containsAllEntries, MapConditions::doesNotContainAllEntries,
                MapConditions::containsAnyEntries, MapConditions::doesNotContainAnyEntries);
        for (var factory : maps) {
            var expected = new ChangingExpectedMap();
            expected.put("key", "before");
            var condition = factory.apply(expected);
            assertEquals(List.of(), condition.references().getFirst().value());
            expected.put("key", "after");
            assertEquals(List.of(), condition.references().getFirst().value());
        }
    }

    @Test
    void collectionFactoriesFreezeMembershipOrderDuplicatesAndNulls() {
        var expected = new ArrayList<>(asList("a", null, "a"));
        assertStable(List.of(containsAll(expected), doesNotContainAny(expected),
                doesNotContainAll(expected), containsAny(expected),
                containsExactly(expected), doesNotContainExactly(expected),
                containsExactlyInAnyOrder(expected), doesNotContainExactlyInAnyOrder(expected),
                containsOnly(expected), subsetOf(expected), startsWith(expected), endsWith(expected),
                containsSequence(expected), doesNotContainSequence(expected),
                containsSubsequence(expected), doesNotContainSubsequence(expected)),
                asList("a", null, "a"), List.of("changed"), () -> {
                    expected.clear();
                    expected.add("changed");
                });
    }

    @Test
    void collectionFactoriesSnapshotArrayBackedInputs() {
        String[] expected = {"a", null, "a"};
        assertStable(List.of(containsAll(asList(expected)), doesNotContainAny(asList(expected)), doesNotContainAll(asList(expected)),
                containsAny(asList(expected)), containsExactly(asList(expected)), doesNotContainExactly(asList(expected)),
                containsExactlyInAnyOrder(asList(expected)), doesNotContainExactlyInAnyOrder(asList(expected)),
                containsOnly(asList(expected)), startsWith(asList(expected)), endsWith(asList(expected)), containsSequence(asList(expected)),
                doesNotContainSequence(asList(expected)), containsSubsequence(asList(expected)), doesNotContainSubsequence(asList(expected))),
                asList("a", null, "a"), List.of("changed"), () -> expected[0] = "changed");
    }

    @Test
    void mapFactoriesFreezeEntriesAcrossMutationAndClearing() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("a", "before");
        expected.put(null, null);
        Map<String, String> original = new LinkedHashMap<>(expected);
        var conditions = List.of(MapConditions.containsAllEntries(expected), MapConditions.doesNotContainAllEntries(expected),
                MapConditions.containsAnyEntries(expected), MapConditions.doesNotContainAnyEntries(expected),
                MapConditions.containsExactlyEntries(expected), MapConditions.doesNotContainExactlyEntries(expected));
        assertStable(conditions, original, Map.of("a", "changed"), () -> {
                    expected.entrySet().iterator().next().setValue("changed");
                    expected.remove(null);
                });
        assertStable(conditions, original, Map.of(), expected::clear);
    }

    @Test
    void mapFactoriesFreezeArrayBackedKeysAndValues() {
        String[] expected = {"a", null};
        var original = new LinkedHashMap<String, String>();
        original.put("a", "a");
        original.put(null, null);
        assertStable(List.of(MapConditions.containsAllKeys(asList(expected)), MapConditions.doesNotContainAnyKeys(asList(expected)),
                MapConditions.containsOnlyKeys(asList(expected)), MapConditions.containsAllValues(asList(expected)),
                MapConditions.doesNotContainAnyValues(asList(expected))), original, Map.of("changed", "changed"),
                () -> expected[0] = "changed");
    }

    @Test
    void candidateFactoriesFreezeTheirInputs() {
        String[] expected = {"before", null};
        var included = Conditions.in(asList(expected));
        var excluded = Conditions.notIn(asList(expected));
        expected[0] = "after";
        assertInstanceOf(Satisfied.class, evaluate(included, "before"));
        assertInstanceOf(Unsatisfied.class, evaluate(included, "after"));
        assertInstanceOf(Unsatisfied.class, evaluate(excluded, "before"));
        assertInstanceOf(Satisfied.class, evaluate(excluded, "after"));
        assertInstanceOf(Satisfied.class, evaluate(included, null));

        String[] fragments = {"before"};
        assertStable(List.of(StringConditions.containsAll(asList(fragments)), StringConditions.doesNotContainAny(asList(fragments))),
                "before", "after", () -> fragments[0] = "after");
    }

    @Test
    void mapSnapshotsKeepDistinctKeysWithoutUserHashingOrEquality() {
        var expected = new IdentityHashMap<String, String>();
        expected.put(new String("key"), "first");
        expected.put(new String("key"), "second");
        var actual = new IdentityHashMap<>(expected);
        var condition = MapConditions.containsExactlyEntries(expected);
        expected.clear();
        assertSame(actual, result(evaluate(condition, actual)));

        var key = new ProbeContainers.Directional(false);
        var withUserKey = new IdentityHashMap<ProbeContainers.Directional, String>();
        withUserKey.put(key, "value");
        var withUserKeyCondition = MapConditions.containsExactlyEntries(withUserKey);
        assertEquals(0, key.equalsCalls);
        assertSame(withUserKey, result(evaluate(withUserKeyCondition, withUserKey)));
    }

    @Test
    void snapshotsKeepElementIdentityAndReadOnlyDiagnosticContainers() {
        var element = new StringBuilder("before");
        var expected = new ArrayList<>(List.of(element));
        var condition = containsAll(expected);
        var reference = assertInstanceOf(List.class, condition.references().getFirst().value());
        expected.clear();
        element.append(" changed");
        assertSame(element, reference.getFirst());
        assertThrows(UnsupportedOperationException.class, reference::clear);
        var actual = List.of(element);
        assertSame(actual, result(evaluate(condition, actual)));

        var strings = StringConditions.containsAll(List.of("before"));
        assertThrows(UnsupportedOperationException.class,
                assertInstanceOf(List.class, strings.references().getFirst().value())::clear);
    }

    @Test
    void mapDiagnosticsKeepTheSnapshotOrderAndFormat() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("b", "before");
        expected.put("a", null);
        var condition = MapConditions.containsAllEntries(expected);
        expected.put("b", "after");
        expected.remove("a");
        assertEquals("[b=before, a=null]", condition.references().getFirst().value().toString());
        var entries = assertInstanceOf(List.class, condition.references().getFirst().value());
        assertThrows(UnsupportedOperationException.class, entries::clear);
        Map.Entry<?, ?> first = assertInstanceOf(Map.Entry.class, entries.getFirst());
        assertThrows(UnsupportedOperationException.class, () -> first.setValue(null));
    }

    private static final class ChangingExpectedQueue extends ConcurrentLinkedQueue<String> {
        @Override
        public Spliterator<String> spliterator() {
            clear();
            return super.spliterator();
        }
    }

    private static final class ChangingExpectedMap extends ConcurrentHashMap<String, String> {
        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            clear();
            return super.entrySet();
        }
    }

    private static <ACTUAL> void assertStable(List<? extends PreservingCondition<? super ACTUAL>> conditions,
            ACTUAL original, ACTUAL changed, Runnable mutate) {
        var before = conditions.stream().map(condition -> List.of(evaluate(condition, original).getClass(),
                evaluate(condition, changed).getClass())).toList();
        mutate.run();
        var after = conditions.stream().map(condition -> List.of(evaluate(condition, original).getClass(),
                evaluate(condition, changed).getClass())).toList();
        assertEquals(before, after);
    }
}
