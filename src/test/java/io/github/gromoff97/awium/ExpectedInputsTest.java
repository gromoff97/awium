package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class ExpectedInputsTest {

    @Test
    void expectedValuesCanDisappearBeforeTheirSnapshotIsTaken() {
        var expected = new ArrayList<>(List.of("before")) {
            @Override
            public boolean isEmpty() {
                boolean empty = super.isEmpty();
                clear();
                return empty;
            }
        };
        var condition = contains(expected);
        assertEquals(List.of(), condition.definition.reference().value());
        expected.add("after");
        for (var actual : List.of(List.<String>of(), List.of("unrelated"))) {
            assertSame(actual, result(evaluate(condition, actual)));
        }
        assertInstanceOf(Unsatisfied.class, evaluate(condition, null));
    }

    @Test
    void collectionFactoriesFreezeMembershipOrderDuplicatesAndNulls() {
        var expected = new ArrayList<>(asList("a", null, "a"));
        assertStable(List.of(contains(expected), doesNotContain(expected),
                doesNotContainAll(expected), containsAny(expected),
                containsExactly(expected), doesNotContainExactly(expected),
                containsExactlyInAnyOrder(expected), doesNotContainExactlyInAnyOrder(expected),
                containsOnly(expected), subsetOf(expected)),
                asList("a", null, "a"), List.of("changed"), () -> {
                    expected.clear();
                    expected.add("changed");
                });
    }

    @Test
    void collectionVarargsFactoriesFreezeTheArray() {
        String[] expected = {"a", null, "a"};
        assertStable(List.of(contains(expected), doesNotContain(expected), doesNotContainAll(expected),
                containsAny(expected), containsExactly(expected), doesNotContainExactly(expected),
                containsExactlyInAnyOrder(expected), doesNotContainExactlyInAnyOrder(expected),
                containsOnly(expected), startsWith(expected), endsWith(expected), containsSequence(expected),
                doesNotContainSequence(expected), containsSubsequence(expected), doesNotContainSubsequence(expected)),
                asList("a", null, "a"), List.of("changed"), () -> expected[0] = "changed");
    }

    @Test
    void mapFactoriesCopyEntriesRatherThanKeepingLiveEntryObjects() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("a", "before");
        expected.put(null, null);
        Map<String, String> original = new LinkedHashMap<>(expected);
        assertStable(List.of(MapConditions.containsEntries(expected), MapConditions.doesNotContainAllEntries(expected),
                MapConditions.containsAnyEntries(expected), MapConditions.doesNotContainEntries(expected),
                MapConditions.containsExactlyEntries(expected), MapConditions.doesNotContainExactlyEntries(expected)),
                original, Map.of("a", "changed"), () -> {
                    expected.entrySet().iterator().next().setValue("changed");
                    expected.remove(null);
                });
    }

    @Test
    void mapVarargsFactoriesFreezeKeysAndValues() {
        String[] expected = {"a", null};
        var original = new LinkedHashMap<String, String>();
        original.put("a", "a");
        original.put(null, null);
        assertStable(List.of(MapConditions.containsKeys(expected), MapConditions.doesNotContainKeys(expected),
                MapConditions.containsOnlyKeys(expected), MapConditions.containsValues(expected),
                MapConditions.doesNotContainValues(expected)), original, Map.of("changed", "changed"),
                () -> expected[0] = "changed");
    }

    @Test
    void objectAndStringVarargsFactoriesFreezeTheirCandidates() {
        String[] expected = {"before", null};
        var included = Conditions.in(expected);
        var excluded = Conditions.notIn(expected);
        expected[0] = "after";
        assertInstanceOf(Satisfied.class, evaluate(included, "before"));
        assertInstanceOf(Unsatisfied.class, evaluate(included, "after"));
        assertInstanceOf(Unsatisfied.class, evaluate(excluded, "before"));
        assertInstanceOf(Satisfied.class, evaluate(excluded, "after"));
        assertInstanceOf(Satisfied.class, evaluate(included, null));

        String[] fragments = {"before"};
        assertStable(List.of(StringConditions.contains(fragments), StringConditions.doesNotContain(fragments)),
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
        var condition = contains(expected);
        var reference = assertInstanceOf(List.class, condition.definition.reference().value());
        expected.clear();
        element.append(" changed");
        assertSame(element, reference.getFirst());
        assertThrows(UnsupportedOperationException.class, reference::clear);
        var actual = List.of(element);
        assertSame(actual, result(evaluate(condition, actual)));

        var strings = StringConditions.contains("before");
        assertThrows(UnsupportedOperationException.class,
                assertInstanceOf(List.class, strings.definition.reference().value())::clear);
    }

    @Test
    void mapDiagnosticsKeepTheSnapshotOrderAndFormat() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("b", "before");
        expected.put("a", null);
        var condition = MapConditions.containsEntries(expected);
        expected.put("b", "after");
        expected.remove("a");
        assertEquals("{b=before, a=null}", condition.definition.reference().value().toString());
    }

    @Test
    void explicitSizeReadsTheExpectedContainerOnlyDuringConstruction() {
        var collection = new ProbeContainers.ProbeCollection<String>(1);
        var map = new ProbeContainers.ProbeMap<String, String>();
        var collectionCondition = size(collection.size()).because("same size");
        var mapCondition = MapConditions.size(map.size()).because("same size");
        var actualCollection = List.of("a");
        var actualMap = Map.of("a", "b");
        assertSame(actualCollection, await(() -> actualCollection).until(collectionCondition));
        assertSame(actualMap, await(() -> actualMap).until(mapCondition));
        assertEquals(1, collection.sizeCalls);
        assertEquals(1, map.sizeCalls);
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
