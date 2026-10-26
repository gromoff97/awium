package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.Spliterator;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static org.junit.jupiter.api.Assertions.*;

class ExpectedInputsTest {

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
        assertEquals(List.of("before"), condition.reference().value());
        expected.clear();
        assertInstanceOf(Unsatisfied.class, evaluate(condition, List.of("unrelated")));
        assertInstanceOf(Unsatisfied.class, evaluate(condition, null));
    }

    @Test
    void nonEmptyExpectationsRejectAnEmptySnapshotAfterRemovalBeforeSnapshot() {
        List<Function<Collection<String>, PreservingCondition<?>>> factories = List.of(
                CollectionConditions::containsAll, CollectionConditions::doesNotContainAny,
                CollectionConditions::doesNotContainAll, CollectionConditions::containsAny,
                CollectionConditions::startsWith, CollectionConditions::endsWith,
                CollectionConditions::containsSequence, CollectionConditions::doesNotContainSequence,
                CollectionConditions::containsSubsequence, CollectionConditions::doesNotContainSubsequence,
                MapConditions::containsAllKeys, MapConditions::doesNotContainAnyKeys,
                MapConditions::containsAllValues, MapConditions::doesNotContainAnyValues);
        for (var factory : factories) {
            var expected = new ChangingExpectedQueue();
            expected.add("before");
            try {
                var condition = factory.apply(expected);
                assertFalse(assertInstanceOf(List.class, condition.reference().value()).isEmpty());
            } catch (IllegalArgumentException failure) {
                assertTrue(failure.getMessage().endsWith("must not be empty"));
            }
        }
    }

    @Test
    void entryConditionsNeverKeepAnEmptySnapshotAfterRemovalBeforeSnapshot() {
        List<Function<Map<String, String>, PreservingCondition<?>>> factories = List.of(
                MapConditions::containsAllEntries, MapConditions::doesNotContainAllEntries,
                MapConditions::containsAnyEntries, MapConditions::doesNotContainAnyEntries);
        for (var factory : factories) {
            var expected = new ChangingExpectedMap();
            expected.put("key", "before");
            try {
                var condition = factory.apply(expected);
                assertFalse(assertInstanceOf(List.class, condition.reference().value()).isEmpty());
            } catch (IllegalArgumentException failure) {
                assertTrue(failure.getMessage().endsWith("must not be empty"));
            }
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
        var reference = assertInstanceOf(List.class, condition.reference().value());
        expected.clear();
        element.append(" changed");
        assertSame(element, reference.getFirst());
        assertThrows(UnsupportedOperationException.class, reference::clear);
        var actual = List.of(element);
        assertSame(actual, result(evaluate(condition, actual)));

        var strings = StringConditions.containsAll(List.of("before"));
        assertThrows(UnsupportedOperationException.class,
                assertInstanceOf(List.class, strings.reference().value())::clear);
    }

    @Test
    void mapDiagnosticsKeepTheSnapshotOrderAndFormat() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("b", "before");
        expected.put("a", null);
        var condition = MapConditions.containsAllEntries(expected);
        expected.put("b", "after");
        expected.remove("a");
        assertEquals("[b=before, a=null]", condition.reference().value().toString());
        var entries = assertInstanceOf(List.class, condition.reference().value());
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
