package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
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
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Condition.ComparisonCondition;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.between;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.greaterThan;
import static io.github.gromoff97.awium.Conditions.in;
import static io.github.gromoff97.awium.Conditions.notEqualTo;
import static io.github.gromoff97.awium.Conditions.notIn;
import static io.github.gromoff97.awium.Conditions.notSameAs;
import static io.github.gromoff97.awium.Conditions.sameAs;
import static io.github.gromoff97.awium.Conditions.strictlyBetween;
import static io.github.gromoff97.awium.MapConditions.containsAllEntries;
import static io.github.gromoff97.awium.MapConditions.containsAllKeys;
import static io.github.gromoff97.awium.MapConditions.containsAllValues;
import static io.github.gromoff97.awium.MapConditions.containsAnyEntries;
import static io.github.gromoff97.awium.MapConditions.containsExactlyEntries;
import static io.github.gromoff97.awium.MapConditions.containsKey;
import static io.github.gromoff97.awium.MapConditions.containsOnlyKeys;
import static io.github.gromoff97.awium.MapConditions.containsValue;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAllEntries;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyEntries;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyKeys;
import static io.github.gromoff97.awium.MapConditions.doesNotContainAnyValues;
import static io.github.gromoff97.awium.MapConditions.doesNotContainExactlyEntries;
import static io.github.gromoff97.awium.MapConditions.doesNotContainKey;
import static io.github.gromoff97.awium.MapConditions.doesNotContainValue;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.StringConditions.containsIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.contentEquals;
import static io.github.gromoff97.awium.StringConditions.doesNotEndWith;
import static io.github.gromoff97.awium.StringConditions.doesNotMatchRegex;
import static io.github.gromoff97.awium.StringConditions.doesNotStartWith;
import static io.github.gromoff97.awium.StringConditions.equalToIgnoringCase;
import static io.github.gromoff97.awium.StringConditions.matchesRegex;
import static io.github.gromoff97.awium.StringConditions.notEqualToIgnoringCase;
import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Map.Entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class ExpectedInputsTest {

    private final FakeTime time = new FakeTime(0);

    @ParameterizedTest
    @MethodSource("collectionOperands")
    void collectionFailuresRetainTheirOperands(Supplier<PreservingCondition<? super List<String>>> factory, List<String> actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> collectionOperands() {
        var operands = List.of("a", "b");
        var actual = List.of("other");
        return List.of(
                operand("exactly in any order", () -> containsExactlyInAnyOrder(operands), actual, "Expected: [a, b]"),
                operand("only", () -> containsOnly(operands), actual, "Expected: [a, b]"),
                operand("subset", () -> subsetOf(operands), actual, "Expected: [a, b]"),
                operand("not exactly", () -> doesNotContainExactly(operands), operands, "Unexpected: [a, b]"),
                operand("not exactly in any order", () -> doesNotContainExactlyInAnyOrder(operands), operands, "Unexpected: [a, b]"));
    }

    @ParameterizedTest
    @MethodSource("mapOperands")
    void mapFailuresRetainTheirOperands(Supplier<PreservingCondition<? super Map<String, String>>> factory, Map<String, String> actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> mapOperands() {
        return List.of(
                operand("missing entry", () -> hasEntry("key", "value"), Map.of("other", "value"), "Expected: key=value"),
                operand("unexpected entry", () -> hasNoEntry("key", "value"), Map.of("key", "value"), "Unexpected: key=value"),
                operand("missing key", () -> containsOnlyKeys(List.of("key")), Map.of("other", "value"), "Expected: [key]"));
    }

    @Test
    void entrySelectionFailureIdentifiesTheMissingKey() {
        assertOperand(tryAwait(() -> Map.of("other", "value")).usingTime(time, time)
                .every(ofNanos(1)).upTo(ofNanos(1)).until(entryFor("key")), "Key: \"key\"");
    }

    @ParameterizedTest
    @MethodSource("stringOperands")
    void stringFailuresRetainTheirOperands(Supplier<PreservingCondition<? super String>> factory, String actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> stringOperands() {
        var needle = "needle";
        var expected = "Expected: \"needle\"";
        var unexpected = "Unexpected: \"needle\"";
        return List.of(
                operand("contains", () -> StringConditions.contains(needle), "other", expected),
                operand("contains ignoring case", () -> containsIgnoringCase(needle), "other", expected),
                operand("starts with", () -> StringConditions.startsWith(needle), "other", expected),
                operand("ends with", () -> StringConditions.endsWith(needle), "other", expected),
                operand("content equals", () -> contentEquals(needle), "other", expected),
                operand("equals ignoring case", () -> equalToIgnoringCase(needle), "other", expected),
                operand("does not contain", () -> StringConditions.doesNotContain(needle), needle, unexpected),
                operand("does not start with", () -> doesNotStartWith(needle), needle, unexpected),
                operand("does not end with", () -> doesNotEndWith(needle), needle, unexpected),
                operand("not equal ignoring case", () -> notEqualToIgnoringCase(needle), needle, unexpected),
                operand("regex", () -> matchesRegex("a.*z"), "other", "Expected: a.*z"),
                operand("negative regex", () -> doesNotMatchRegex("a.*z"), "abz", "Unexpected: a.*z"));
    }

    @ParameterizedTest
    @MethodSource("equalityOperands")
    void equalityFailuresRetainTheirOperands(Supplier<ComparisonCondition<String>> factory, String actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> equalityOperands() {
        var needle = new String("needle");
        return List.of(operand("equality", () -> equalTo(needle), "other", "Expected: \"needle\""),
                operand("identity", () -> sameAs(needle), "other", "Expected: \"needle\""),
                operand("inequality", () -> notEqualTo(needle), needle, "Unexpected: \"needle\""),
                operand("different identity", () -> notSameAs(needle), needle, "Unexpected: \"needle\""));
    }

    @ParameterizedTest
    @MethodSource("comparisonOperands")
    void comparisonFailuresRetainTheirOperands(Supplier<PreservingCondition<Integer>> factory, int actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> comparisonOperands() {
        return List.of(operand("inclusive range", () -> between(1, 5), 10, "Expected: [1, 5]"),
                operand("exclusive range", () -> strictlyBetween(1, 5), 10, "Expected: [1, 5]"),
                operand("greater than", () -> greaterThan(5), 0, "Expected: 5"));
    }

    // Construct the condition inside the selected test so PIT can attribute factory calls to it.
    private static <CHECK extends Condition> Arguments operand(String name, Supplier<CHECK> factory, Object actual, String expected) {
        return argumentSet(name, factory, actual, expected);
    }

    private static void assertOperand(AwaitResult<?, ?> result, String operand) {
        assertThat(failure(result).message()).contains(operand);
        assertThat(result.conditions().getFirst().toString()).contains(operand);
    }

    @TestFactory
    Stream<DynamicTest> singleOperandsRemainWholeValuesInReferences() {
        int[] operand = {1, 2};
        return Stream.of(
                dynamicTest("contains", () -> assertSame(operand, contains(operand).references().getFirst().value())),
                dynamicTest("doesNotContain", () -> assertSame(operand, doesNotContain(operand).references().getFirst().value())),
                dynamicTest("containsKey", () -> assertSame(operand, containsKey(operand).references().getFirst().value())),
                dynamicTest("doesNotContainKey", () -> assertSame(operand, doesNotContainKey(operand).references().getFirst().value())),
                dynamicTest("containsValue", () -> assertSame(operand, containsValue(operand).references().getFirst().value())),
                dynamicTest("doesNotContainValue", () -> assertSame(operand, doesNotContainValue(operand).references().getFirst().value())));
    }

    @ParameterizedTest
    @MethodSource("arrayOperands")
    void arrayOperandsRemainWholeValuesInDiagnostics(Supplier<PreservingCondition<? super List<int[]>>> factory, List<int[]> actual, String operand) {
        assertOperand(tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1)).until(factory.get()), operand);
    }

    private static List<Arguments> arrayOperands() {
        int[] expected = {1, 2};
        return List.of(operand("contains", () -> contains(expected), List.of(new int[] {9}), "Expected: [1, 2]"),
                operand("does not contain", () -> doesNotContain(expected), List.of(expected), "Unexpected: [1, 2]"));
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
        var conditions = List.of(containsAllEntries(expected), doesNotContainAllEntries(expected),
                containsAnyEntries(expected), doesNotContainAnyEntries(expected),
                containsExactlyEntries(expected), doesNotContainExactlyEntries(expected));
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
        assertStable(List.of(containsAllKeys(asList(expected)), doesNotContainAnyKeys(asList(expected)),
                containsOnlyKeys(asList(expected)), containsAllValues(asList(expected)),
                doesNotContainAnyValues(asList(expected))), original, Map.of("changed", "changed"),
                () -> expected[0] = "changed");
    }

    @Test
    void candidateFactoriesFreezeTheirInputs() {
        String[] expected = {"before", null};
        var included = in(asList(expected));
        var excluded = notIn(asList(expected));
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
        var condition = containsExactlyEntries(expected);
        expected.clear();
        assertSame(actual, result(evaluate(condition, actual)));

        var key = new Directional(false);
        var withUserKey = new IdentityHashMap<Directional, String>();
        withUserKey.put(key, "value");
        var withUserKeyCondition = containsExactlyEntries(withUserKey);
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
        var condition = containsAllEntries(expected);
        expected.put("b", "after");
        expected.remove("a");
        assertEquals("[b=before, a=null]", condition.references().getFirst().value().toString());
        var entries = assertInstanceOf(List.class, condition.references().getFirst().value());
        assertThrows(UnsupportedOperationException.class, entries::clear);
        Entry<?, ?> first = assertInstanceOf(Entry.class, entries.getFirst());
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
        public Set<Entry<String, String>> entrySet() {
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
