package io.github.gromoff97.awium.conditions.diagnostics

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.conditions.collection.CollectionConditions
import io.github.gromoff97.awium.conditions.map.MapConditions
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNull
import io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf
import io.github.gromoff97.awium.conditions.common.CommonConditions.exactlyInstanceOf
import io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull
import io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo
import io.github.gromoff97.awium.conditions.common.CommonConditions.asserted
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.present
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.absent
import io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.contains
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContain
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAll
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainAll
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsAny
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainAny
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsExactly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactly
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.containsExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.collection.CollectionConditions.doesNotContainExactlyInAnyOrder
import io.github.gromoff97.awium.conditions.map.MapConditions.containsKey
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainKey
import io.github.gromoff97.awium.conditions.map.MapConditions.containsValue
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainValue
import io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAllEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.containsAnyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainAnyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.containsExactlyEntries
import io.github.gromoff97.awium.conditions.map.MapConditions.doesNotContainExactlyEntries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class ConditionDescriptionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptions")
    fun `built-in descriptions state the expected property`(expected: String, condition: AwaitCondition) {
        assertEquals(expected, condition.description())
    }

    companion object {
        @JvmStatic
        fun descriptions() = listOf(
            arguments("value is null", isNull),
            arguments("value is an instance of java.lang.String", instanceOf(String::class.java)),
            arguments("value is exactly an instance of java.lang.String", exactlyInstanceOf(String::class.java)),
            arguments("value is not null", isNotNull),
            arguments("value equals expected", equalTo("expected")),
            arguments("value does not equal unexpected", notEqualTo("unexpected")),
            arguments("optional is present", present),
            arguments("optional is absent", absent),
            arguments("optional value: value equals expected", hasValue(equalTo("expected"))),
            arguments("optional value: value does not equal unexpected",
                    hasValue(notEqualTo("unexpected"))),
            arguments("value satisfies assertion", asserted<Any> {}),
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
            arguments("collection contains all expected elements", containsAll(listOf("first", "second"))),
            arguments("collection does not contain all unexpected elements",
                    doesNotContainAll(listOf("first", "second"))),
            arguments("collection contains an expected element", containsAny(listOf("expected"))),
            arguments("collection does not contain any unexpected elements", doesNotContainAny(listOf("expected"))),
            arguments("collection contains exactly the expected elements", containsExactly(listOf("expected"))),
            arguments("collection does not contain exactly the unexpected elements",
                    doesNotContainExactly(listOf("expected"))),
            arguments("collection contains exactly the expected elements in any order",
                    containsExactlyInAnyOrder(listOf("expected"))),
            arguments("collection does not contain exactly the unexpected elements in any order",
                    doesNotContainExactlyInAnyOrder(listOf("expected"))),
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
            arguments("map contains all expected entries", containsAllEntries(mapOf("key" to "value"))),
            arguments("map does not contain all unexpected entries",
                    doesNotContainAllEntries(mapOf("key" to "value"))),
            arguments("map contains an expected entry", containsAnyEntries(mapOf("key" to "value"))),
            arguments("map does not contain any unexpected entries",
                    doesNotContainAnyEntries(mapOf("key" to "value"))),
            arguments("map contains exactly the expected entries", containsExactlyEntries(mapOf("key" to "value"))),
            arguments("map does not contain exactly the unexpected entries",
                    doesNotContainExactlyEntries(mapOf("key" to "value")))
        )
    }
}
