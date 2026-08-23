package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
import io.github.gromoff97.awium.conditioning.conditions.OptionalCondition;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltInExpectationGrammarTest {

    @Test
    void builtInExpectationsAreBooleanStatements() {
        assertEquals(List.of(
                "value is null",
                "value is not null",
                "value equals expected",
                "value does not equal unexpected",
                "optional is present",
                "optional is absent",
                "optional value equals expected",
                "optional value does not equal unexpected",
                "value satisfies assertion",
                "callback yields a result",
                "collection has a single element",
                "collection is empty",
                "collection is not empty",
                "collection size is 2",
                "collection size is not 2",
                "collection size is greater than 2",
                "collection size is at least 2",
                "collection size is less than 2",
                "collection size is at most 2",
                "collection contains expected element",
                "collection does not contain expected element",
                "collection contains all expected elements",
                "collection does not contain all expected elements",
                "collection contains expected element",
                "collection does not contain expected element",
                "collection contains exactly the expected elements",
                "collection does not contain exactly the expected elements",
                "collection contains exactly the expected elements in any order",
                "collection does not contain exactly the expected elements in any order",
                "map has a single entry",
                "map is empty",
                "map is not empty",
                "map size is 2",
                "map size is not 2",
                "map size is greater than 2",
                "map size is at least 2",
                "map size is less than 2",
                "map size is at most 2",
                "map contains expected key",
                "map does not contain expected key",
                "map contains expected value",
                "map does not contain expected value",
                "map contains expected entry",
                "map does not contain expected entry",
                "map contains all expected entries",
                "map does not contain all expected entries",
                "map contains an expected entry",
                "map does not contain an expected entry",
                "map contains exactly the expected entries",
                "map does not contain exactly the expected entries"),
                List.of(
                        isNull.description(),
                        description(isNotNull),
                        description(equalTo("expected")),
                        description(notEqualTo("unexpected")),
                        present.delegate().description(),
                        absent.description(),
                        OptionalCondition.hasValue("expected").description(),
                        doesNotHaveValue("unexpected").description(),
                        description(asserted(value -> {})),
                        yields(value -> {
                            return value;
                        }).description(),
                        CollectionCondition.singleElement.description(),
                        CollectionCondition.noElements.delegate().description(),
                        CollectionCondition.hasElements.delegate().description(),
                        CollectionCondition.elementCount(2).delegate().description(),
                        CollectionCondition.elementCountIsNot(2).delegate().description(),
                        CollectionCondition.elementCountGreaterThan(2).delegate().description(),
                        CollectionCondition.elementCountAtLeast(2).delegate().description(),
                        CollectionCondition.elementCountLessThan(2).delegate().description(),
                        CollectionCondition.elementCountAtMost(2).delegate().description(),
                        description(contains("expected")),
                        description(doesNotContain("expected")),
                        description(contains("first", "second")),
                        description(doesNotContainAll("first", "second")),
                        description(containsAnyOf("expected")),
                        description(doesNotContain("expected")),
                        description(containsExactly("expected")),
                        description(doesNotContainExactly("expected")),
                        description(containsExactlyInAnyOrder("expected")),
                        description(doesNotContainExactlyInAnyOrder("expected")),
                        MapCondition.singleEntry.description(),
                        MapCondition.noEntries.delegate().description(),
                        MapCondition.hasEntries.delegate().description(),
                        MapCondition.entryCount(2).delegate().description(),
                        MapCondition.entryCountNot(2).delegate().description(),
                        MapCondition.entryCountGreaterThan(2).delegate().description(),
                        MapCondition.entryCountAtLeast(2).delegate().description(),
                        MapCondition.entryCountLessThan(2).delegate().description(),
                        MapCondition.entryCountAtMost(2).delegate().description(),
                        description(containsKey("expected")),
                        description(doesNotContainKey("expected")),
                        description(containsValue("expected")),
                        description(doesNotContainValue("expected")),
                        description(containsEntry("key", "value")),
                        description(doesNotContainEntry("key", "value")),
                        description(containsAllEntriesOf(Map.of("key", "value"))),
                        description(doesNotContainAllEntriesOf(
                                Map.of("key", "value"))),
                        description(containsAnyEntriesOf(Map.of("key", "value"))),
                        description(containsNoEntriesOf(Map.of("key", "value"))),
                        description(containsExactlyEntriesOf(
                                Map.of("key", "value"))),
                        description(doesNotContainExactlyEntriesOf(
                                Map.of("key", "value")))));
    }

    private static String description(Condition<?, ?> condition) {
        return condition.description();
    }

    private static String description(PreservingCondition<?> condition) {
        return condition.delegate().description();
    }
}
