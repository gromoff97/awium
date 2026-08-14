package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static java.time.Duration.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNull("source", () -> await((Source<Object>) null));
        assertNull("source", () -> await(
                (CollectionSource<Collection<Object>>) null));
        assertNull("source", () -> await(
                (MapSource<Map<Object, Object>>) null));
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        int[] calls = {0};
        var initial = await(() -> "v" + ++calls[0]);

        var slow = initial.every(ofSeconds(20));
        var repaired = slow
                .upTo(ofSeconds(10))
                .every(ofMillis(1))
                .upTo(ofSeconds(1))
                .stableFor(ofSeconds(2))
                .stableFor(ZERO);

        assertThrows(AwaitConfigurationConflictException.class,
                () -> slow.until(isNotNull));
        assertEquals("v1", repaired.until(isNotNull));
    }

    @Test
    void nullConditionWinsOverFinalConfigurationConflictForEveryOverload() {
        var object = await((Source<String>) () -> "value")
                .every(ofSeconds(20));
        assertNull("condition", () -> object.until((PreservingCondition<String>) null));
        assertNull("condition", () -> object.until(
                (PreservingCondition.ExplainedCondition<String>) null));
        assertNull("condition", () -> object.until((Condition<String, String>) null));
        assertNull("condition", () -> object.until(
                (Condition.ExplainedCondition<String, String>) null));

        var optional = await((OptionalSource<String>) Optional::empty)
                .every(ofSeconds(20));
        assertNull("condition", () -> optional.until((PresentCondition) null));
        assertNull("condition", () -> optional.until((PresentCondition.ExplainedCondition) null));

        var collection = await((CollectionSource<Collection<String>>) List::of).every(ofSeconds(20));
        assertNull("condition", () -> collection.until((StructuralCondition) null));
        assertNull("condition", () -> collection.until(
                (StructuralCondition.ExplainedCondition) null));
    }

    private static void assertNull(String context, Executable action) {
        assertTrue(assertThrows(NullPointerException.class, action).getMessage().contains(context));
    }
}
