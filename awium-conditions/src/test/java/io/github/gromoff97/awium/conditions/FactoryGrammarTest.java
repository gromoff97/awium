package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.CheckedBiPredicate;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.CollectionConditions.any;
import static io.github.gromoff97.awium.conditions.CollectionConditions.none;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactoryGrammarTest {

    @Test
    void conditionFactoriesRejectMissingChecks() {
        assertNullRejected("predicate", () -> matches((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> singleElement((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> hasValue((CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> valueFor("key", (CheckedPredicate<String>) null));
        assertNullRejected("predicate", () -> singleEntry((CheckedBiPredicate<String, String>) null));
        assertNullRejected("predicate", () -> singleEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> allEntries((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> hasEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("predicate", () -> hasNoEntry((CheckedPredicate<Map.Entry<String, String>>) null));
        assertNullRejected("condition", () -> all((PreservingCondition<String>) null));
        assertNullRejected("condition", () -> any((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> none((Condition<Object, String>) null));
        assertNullRejected("condition", () -> valueFor("key", (PreservingCondition<String>) null));
        assertNullRejected("condition", () -> valueFor("key", (Condition<Object, String>) null));
        assertNullRejected("condition", () -> hasValue((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> singleElement((AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> valueFor("key", (AwaitCondition.ComparisonCondition<String>) null));
        assertNullRejected("condition", () -> singleEntry((AwaitCondition.ComparisonCondition<Map.Entry<String, String>>) null));
    }

    private static void assertNullRejected(String argument, Executable action) {
        assertEquals(argument + " must not be null", assertThrows(NullPointerException.class, action).getMessage());
    }
}
