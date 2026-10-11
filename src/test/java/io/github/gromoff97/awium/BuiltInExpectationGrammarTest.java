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
                "optional value: value equals expected",
                "optional value: value does not equal unexpected",
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
                "map contains all expected keys",
                "map does not contain unexpected keys",
                "map contains all expected values",
                "map does not contain unexpected values",
                "map contains expected entry",
                "map does not contain expected entry",
                "map contains all expected entries",
                "map does not contain all expected entries",
                "map contains an expected entry",
                "map does not contain an expected entry",
                "map contains exactly the expected entries",
                "map does not contain exactly the expected entries"),
                List.of(
                        description(isNull),
                        description(isNotNull),
                        description(equalTo("expected")),
                        description(notEqualTo("unexpected")),
                        description(present),
                        description(absent),
                        description(OptionalConditions.hasValue(equalTo("expected"))),
                        description(OptionalConditions.hasValue(notEqualTo("unexpected"))),
                        description(asserted(value -> {})),
                        description(yields(value -> {
                            return value;
                        })),
                        description(CollectionConditions.single),
                        description(CollectionConditions.empty),
                        description(CollectionConditions.nonEmpty),
                        description(CollectionConditions.size(2)),
                        description(CollectionConditions.sizeIsNot(2)),
                        description(CollectionConditions.sizeGreaterThan(2)),
                        description(CollectionConditions.sizeAtLeast(2)),
                        description(CollectionConditions.sizeLessThan(2)),
                        description(CollectionConditions.sizeAtMost(2)),
                        description(contains("expected")),
                        description(doesNotContain("expected")),
                        description(contains("first", "second")),
                        description(doesNotContainAll("first", "second")),
                        description(containsAny("expected")),
                        description(doesNotContain("expected")),
                        description(containsExactly("expected")),
                        description(doesNotContainExactly("expected")),
                        description(containsExactlyInAnyOrder("expected")),
                        description(doesNotContainExactlyInAnyOrder("expected")),
                        description(MapConditions.singleEntry),
                        description(MapConditions.empty),
                        description(MapConditions.nonEmpty),
                        description(MapConditions.size(2)),
                        description(MapConditions.sizeIsNot(2)),
                        description(MapConditions.sizeGreaterThan(2)),
                        description(MapConditions.sizeAtLeast(2)),
                        description(MapConditions.sizeLessThan(2)),
                        description(MapConditions.sizeAtMost(2)),
                        description(containsKeys("expected")),
                        description(doesNotContainKeys("expected")),
                        description(containsValues("expected")),
                        description(doesNotContainValues("expected")),
                        description(containsEntries("key", "value")),
                        description(doesNotContainEntries("key", "value")),
                        description(containsEntries(Map.of("key", "value"))),
                        description(doesNotContainAllEntries(
                                Map.of("key", "value"))),
                        description(containsAnyEntries(Map.of("key", "value"))),
                        description(doesNotContainEntries(Map.of("key", "value"))),
                        description(containsExactlyEntries(
                                Map.of("key", "value"))),
                        description(doesNotContainExactlyEntries(
                                Map.of("key", "value")))));
    }

}
