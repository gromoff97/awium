package io.github.gromoff97.awium;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltInConditionGrammarTest {

    @Test
    void builtInConditionsAreBooleanStatements() {
        assertEquals("value is null", isNull.description());
        assertEquals("value is not null", isNotNull.description());
        assertEquals("value equals expected", equalTo("expected").description());
        assertEquals("value does not equal unexpected", notEqualTo("unexpected").description());
        assertEquals("optional is present", present.description());
        assertEquals("optional is absent", absent.description());
        assertEquals("optional value: value equals expected",
                OptionalConditions.hasValue(equalTo("expected")).description());
        assertEquals("optional value: value does not equal unexpected",
                OptionalConditions.hasValue(notEqualTo("unexpected")).description());
        assertEquals("value satisfies assertion", asserted(value -> {}).description());
        assertEquals("collection has a single element", CollectionConditions.singleElement.description());
        assertEquals("collection is empty", CollectionConditions.empty.description());
        assertEquals("collection is not empty", CollectionConditions.nonEmpty.description());
        assertEquals("collection size is 2", CollectionConditions.hasSize(2).description());
        assertEquals("collection size is not 2", CollectionConditions.hasSizeNotEqualTo(2).description());
        assertEquals("collection size is greater than 2", CollectionConditions.hasSizeGreaterThan(2).description());
        assertEquals("collection size is at least 2", CollectionConditions.hasSizeAtLeast(2).description());
        assertEquals("collection size is less than 2", CollectionConditions.hasSizeLessThan(2).description());
        assertEquals("collection size is at most 2", CollectionConditions.hasSizeAtMost(2).description());
        assertEquals("collection contains expected element", contains("expected").description());
        assertEquals("collection does not contain unexpected element", doesNotContain("expected").description());
        assertEquals("collection contains all expected elements", containsAll(List.of("first", "second")).description());
        assertEquals("collection does not contain all unexpected elements",
                doesNotContainAll(List.of("first", "second")).description());
        assertEquals("collection contains an expected element", containsAny(List.of("expected")).description());
        assertEquals("collection does not contain any unexpected elements",
                doesNotContainAny(List.of("expected")).description());
        assertEquals("collection contains exactly the expected elements",
                containsExactly(List.of("expected")).description());
        assertEquals("collection does not contain exactly the unexpected elements",
                doesNotContainExactly(List.of("expected")).description());
        assertEquals("collection contains exactly the expected elements in any order",
                containsExactlyInAnyOrder(List.of("expected")).description());
        assertEquals("collection does not contain exactly the unexpected elements in any order",
                doesNotContainExactlyInAnyOrder(List.of("expected")).description());
        assertEquals("map has a single entry", MapConditions.singleEntry.description());
        assertEquals("map is empty", MapConditions.empty.description());
        assertEquals("map is not empty", MapConditions.nonEmpty.description());
        assertEquals("map size is 2", MapConditions.hasSize(2).description());
        assertEquals("map size is not 2", MapConditions.hasSizeNotEqualTo(2).description());
        assertEquals("map size is greater than 2", MapConditions.hasSizeGreaterThan(2).description());
        assertEquals("map size is at least 2", MapConditions.hasSizeAtLeast(2).description());
        assertEquals("map size is less than 2", MapConditions.hasSizeLessThan(2).description());
        assertEquals("map size is at most 2", MapConditions.hasSizeAtMost(2).description());
        assertEquals("map contains expected key", containsKey("expected").description());
        assertEquals("map does not contain unexpected key", doesNotContainKey("expected").description());
        assertEquals("map contains expected value", containsValue("expected").description());
        assertEquals("map does not contain unexpected value", doesNotContainValue("expected").description());
        assertEquals("map contains expected entry", hasEntry("key", "value").description());
        assertEquals("map does not contain unexpected entry", hasNoEntry("key", "value").description());
        assertEquals("map contains all expected entries", containsAllEntries(Map.of("key", "value")).description());
        assertEquals("map does not contain all unexpected entries",
                doesNotContainAllEntries(Map.of("key", "value")).description());
        assertEquals("map contains an expected entry", containsAnyEntries(Map.of("key", "value")).description());
        assertEquals("map does not contain any unexpected entries",
                doesNotContainAnyEntries(Map.of("key", "value")).description());
        assertEquals("map contains exactly the expected entries",
                containsExactlyEntries(Map.of("key", "value")).description());
        assertEquals("map does not contain exactly the unexpected entries",
                doesNotContainExactlyEntries(Map.of("key", "value")).description());
    }

}
