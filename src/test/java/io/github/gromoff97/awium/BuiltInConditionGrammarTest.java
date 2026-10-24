package io.github.gromoff97.awium;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltInConditionGrammarTest {

    @Test
    void builtInConditionsAreBooleanStatements() {
        assertEquals("value is null", description(isNull));
        assertEquals("value is not null", description(isNotNull));
        assertEquals("value equals expected", description(equalTo("expected")));
        assertEquals("value does not equal unexpected", description(notEqualTo("unexpected")));
        assertEquals("optional is present", description(present));
        assertEquals("optional is absent", description(absent));
        assertEquals("optional value: value equals expected",
                description(OptionalConditions.hasValue(equalTo("expected"))));
        assertEquals("optional value: value does not equal unexpected",
                description(OptionalConditions.hasValue(notEqualTo("unexpected"))));
        assertEquals("value satisfies assertion", description(asserted(value -> {})));
        assertEquals("collection has a single element", description(CollectionConditions.single));
        assertEquals("collection is empty", description(CollectionConditions.empty));
        assertEquals("collection is not empty", description(CollectionConditions.nonEmpty));
        assertEquals("collection size is 2", description(CollectionConditions.size(2)));
        assertEquals("collection size is not 2", description(CollectionConditions.sizeIsNot(2)));
        assertEquals("collection size is greater than 2", description(CollectionConditions.sizeGreaterThan(2)));
        assertEquals("collection size is at least 2", description(CollectionConditions.sizeAtLeast(2)));
        assertEquals("collection size is less than 2", description(CollectionConditions.sizeLessThan(2)));
        assertEquals("collection size is at most 2", description(CollectionConditions.sizeAtMost(2)));
        assertEquals("collection contains all expected elements", description(contains("expected")));
        assertEquals("collection does not contain any unexpected elements", description(doesNotContain("expected")));
        assertEquals("collection contains all expected elements", description(containsAll(List.of("first", "second"))));
        assertEquals("collection does not contain all unexpected elements",
                description(doesNotContainAll(List.of("first", "second"))));
        assertEquals("collection contains an expected element", description(containsAny(List.of("expected"))));
        assertEquals("collection does not contain any unexpected elements",
                description(doesNotContainAny(List.of("expected"))));
        assertEquals("collection contains exactly the expected elements",
                description(containsExactly(List.of("expected"))));
        assertEquals("collection does not contain exactly the expected elements",
                description(doesNotContainExactly(List.of("expected"))));
        assertEquals("collection contains exactly the expected elements in any order",
                description(containsExactlyInAnyOrder(List.of("expected"))));
        assertEquals("collection does not contain exactly the expected elements in any order",
                description(doesNotContainExactlyInAnyOrder(List.of("expected"))));
        assertEquals("map has a single entry", description(MapConditions.singleEntry));
        assertEquals("map is empty", description(MapConditions.empty));
        assertEquals("map is not empty", description(MapConditions.nonEmpty));
        assertEquals("map size is 2", description(MapConditions.size(2)));
        assertEquals("map size is not 2", description(MapConditions.sizeIsNot(2)));
        assertEquals("map size is greater than 2", description(MapConditions.sizeGreaterThan(2)));
        assertEquals("map size is at least 2", description(MapConditions.sizeAtLeast(2)));
        assertEquals("map size is less than 2", description(MapConditions.sizeLessThan(2)));
        assertEquals("map size is at most 2", description(MapConditions.sizeAtMost(2)));
        assertEquals("map contains all expected keys", description(containsKey("expected")));
        assertEquals("map does not contain unexpected keys", description(doesNotContainKey("expected")));
        assertEquals("map contains all expected values", description(containsValue("expected")));
        assertEquals("map does not contain unexpected values", description(doesNotContainValue("expected")));
        assertEquals("map contains all expected entries", description(containsEntries("key", "value")));
        assertEquals("map does not contain an expected entry", description(doesNotContainEntries("key", "value")));
        assertEquals("map contains all expected entries", description(containsEntries(Map.of("key", "value"))));
        assertEquals("map does not contain all expected entries",
                description(doesNotContainAllEntries(Map.of("key", "value"))));
        assertEquals("map contains an expected entry", description(containsAnyEntries(Map.of("key", "value"))));
        assertEquals("map does not contain an expected entry",
                description(doesNotContainEntries(Map.of("key", "value"))));
        assertEquals("map contains exactly the expected entries",
                description(containsExactlyEntries(Map.of("key", "value"))));
        assertEquals("map does not contain exactly the expected entries",
                description(doesNotContainExactlyEntries(Map.of("key", "value"))));
    }

}
