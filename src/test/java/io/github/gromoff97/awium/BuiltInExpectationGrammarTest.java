package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.MapConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider;

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
                "assertion passes",
                "assertion passes",
                "collection is empty",
                "collection is not empty",
                "collection size is exactly 2",
                "collection size is not exactly 2",
                "collection size is greater than 2",
                "collection size is at least 2",
                "collection size is less than 2",
                "collection size is at most 2",
                "collection contains expected element",
                "collection does not contain expected element",
                "collection contains all expected elements",
                "collection does not contain all expected elements",
                "collection contains any expected element",
                "collection does not contain any expected element",
                "collection contains exactly the expected elements",
                "collection does not contain exactly the expected elements",
                "collection contains exactly the expected elements in any order",
                "collection does not contain exactly the expected elements in any order",
                "map contains expected key",
                "map does not contain expected key",
                "map contains expected value",
                "map does not contain expected value",
                "map contains expected entry",
                "map does not contain expected entry",
                "map contains all expected entries",
                "map does not contain all expected entries",
                "map contains any expected entry",
                "map does not contain any expected entry",
                "map contains exactly the expected entries",
                "map does not contain exactly the expected entries"),
                List.of(
                        isNull.description(),
                        description(isNotNull),
                        description(equalTo("expected")),
                        description(notEqualTo("unexpected")),
                        RuntimeCondition.<Object>present(
                                OptionalConditionProvider.present)
                                .description().get(),
                        absent.description(),
                        hasValueEqualTo("expected").description(),
                        hasValueNotEqualTo("unexpected").description(),
                        description(asserted(value -> {})),
                        passed(value -> value).description(),
                        structural(empty, "collection", ignored -> 0)
                                .description().get(),
                        structural(nonEmpty, "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeExactly(2), "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeNotExactly(2), "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeGreaterThan(2), "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeAtLeast(2), "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeLessThan(2), "collection", ignored -> 0)
                                .description().get(),
                        structural(sizeAtMost(2), "collection", ignored -> 0)
                                .description().get(),
                        description(contains("expected")),
                        description(doesNotContain("expected")),
                        description(containsAll("expected")),
                        description(doesNotContainAll("expected")),
                        description(containsAnyOf("expected")),
                        description(containsNoneOf("expected")),
                        description(containsExactly("expected")),
                        description(doesNotContainExactly("expected")),
                        description(containsExactlyInAnyOrder("expected")),
                        description(doesNotContainExactlyInAnyOrder("expected")),
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
        return condition.runtime().description().get();
    }
}
