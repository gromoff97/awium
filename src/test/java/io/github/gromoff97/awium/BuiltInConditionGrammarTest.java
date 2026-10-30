package io.github.gromoff97.awium;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BuiltInConditionGrammarTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("conditions")
    void builtInConditionsAreBooleanStatements(String expected, Condition condition) {
        assertEquals(expected, condition.description());
    }

    private static List<Arguments> conditions() {
        return List.of(
                arguments("value is null", isNull),
                arguments("value is an instance of java.lang.String", instanceOf(String.class)),
                arguments("value is exactly an instance of java.lang.String", exactlyInstanceOf(String.class)),
                arguments("value is not null", isNotNull),
                arguments("value equals expected", equalTo("expected")),
                arguments("value does not equal unexpected", notEqualTo("unexpected")),
                arguments("optional is present", present),
                arguments("optional is absent", absent),
                arguments("optional value: value equals expected", hasValue(equalTo("expected"))),
                arguments("optional value: value does not equal unexpected",
                        hasValue(notEqualTo("unexpected"))),
                arguments("value satisfies assertion", asserted(value -> {})),
                arguments("collection has a single element", CollectionConditions.singleElement),
                arguments("collection is empty", CollectionConditions.empty),
                arguments("collection is not empty", CollectionConditions.nonEmpty),
                arguments("collection size is 2", CollectionConditions.hasSize(2)),
                arguments("collection size is not 2", CollectionConditions.hasSizeNotEqualTo(2)),
                arguments("collection size is greater than 2", CollectionConditions.hasSizeGreaterThan(2)),
                arguments("collection size is at least 2", CollectionConditions.hasSizeAtLeast(2)),
                arguments("collection size is less than 2", CollectionConditions.hasSizeLessThan(2)),
                arguments("collection size is at most 2", CollectionConditions.hasSizeAtMost(2)),
                arguments("collection contains expected element", contains("expected")),
                arguments("collection does not contain unexpected element", doesNotContain("expected")),
                arguments("collection contains all expected elements", containsAll(List.of("first", "second"))),
                arguments("collection does not contain all unexpected elements",
                        doesNotContainAll(List.of("first", "second"))),
                arguments("collection contains an expected element", containsAny(List.of("expected"))),
                arguments("collection does not contain any unexpected elements", doesNotContainAny(List.of("expected"))),
                arguments("collection contains exactly the expected elements", containsExactly(List.of("expected"))),
                arguments("collection does not contain exactly the unexpected elements",
                        doesNotContainExactly(List.of("expected"))),
                arguments("collection contains exactly the expected elements in any order",
                        containsExactlyInAnyOrder(List.of("expected"))),
                arguments("collection does not contain exactly the unexpected elements in any order",
                        doesNotContainExactlyInAnyOrder(List.of("expected"))),
                arguments("map has a single entry", MapConditions.singleEntry),
                arguments("map is empty", MapConditions.empty),
                arguments("map is not empty", MapConditions.nonEmpty),
                arguments("map size is 2", MapConditions.hasSize(2)),
                arguments("map size is not 2", MapConditions.hasSizeNotEqualTo(2)),
                arguments("map size is greater than 2", MapConditions.hasSizeGreaterThan(2)),
                arguments("map size is at least 2", MapConditions.hasSizeAtLeast(2)),
                arguments("map size is less than 2", MapConditions.hasSizeLessThan(2)),
                arguments("map size is at most 2", MapConditions.hasSizeAtMost(2)),
                arguments("map contains expected key", containsKey("expected")),
                arguments("map does not contain unexpected key", doesNotContainKey("expected")),
                arguments("map contains expected value", containsValue("expected")),
                arguments("map does not contain unexpected value", doesNotContainValue("expected")),
                arguments("map contains expected entry", hasEntry("key", "value")),
                arguments("map does not contain unexpected entry", hasNoEntry("key", "value")),
                arguments("map contains all expected entries", containsAllEntries(Map.of("key", "value"))),
                arguments("map does not contain all unexpected entries",
                        doesNotContainAllEntries(Map.of("key", "value"))),
                arguments("map contains an expected entry", containsAnyEntries(Map.of("key", "value"))),
                arguments("map does not contain any unexpected entries",
                        doesNotContainAnyEntries(Map.of("key", "value"))),
                arguments("map contains exactly the expected entries", containsExactlyEntries(Map.of("key", "value"))),
                arguments("map does not contain exactly the unexpected entries",
                        doesNotContainExactlyEntries(Map.of("key", "value"))));
    }
}
