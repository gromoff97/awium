package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullSource(() -> await((Source<Object>) null));
        assertNullSource(() -> await(
                (CollectionSource<Collection<Object>>) null));
        assertNullSource(() -> await(
                (MapSource<Map<Object, Object>>) null));
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        int[] calls = {0};
        Await<String> initial = await(() -> "v" + ++calls[0]);

        Await<String> slow = initial.every(Duration.ofSeconds(20));
        Await<String> repaired = slow
                .upTo(Duration.ofSeconds(10))
                .every(Duration.ofMillis(1))
                .upTo(Duration.ofSeconds(1))
                .stableFor(Duration.ofSeconds(2))
                .stableFor(Duration.ZERO);

        assertThrows(AwaitConfigurationConflictException.class,
                () -> slow.until(isNotNull));
        assertEquals("v1", repaired.until(isNotNull));
    }

    @Test
    void nullConditionWinsOverFinalConfigurationConflictForEveryOverload() {
        Await<String> object = await((Source<String>) () -> "value")
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> object.until((PreservingCondition<String>) null));
        assertNullCondition(() -> object.until(
                (PreservingCondition.ExplainedCondition<String>) null));
        assertNullCondition(() -> object.until((Condition<String, String>) null));
        assertNullCondition(() -> object.until(
                (Condition.ExplainedCondition<String, String>) null));

        OptionalAwait<String> optional = await((OptionalSource<String>) Optional::empty)
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> optional.until((PresentCondition) null));
        assertNullCondition(() -> optional.until((PresentCondition.ExplainedCondition) null));

        StructuralAwait<Collection<String>> collection =
                await((CollectionSource<Collection<String>>) List::of)
                        .every(Duration.ofSeconds(20));
        assertNullCondition(() -> collection.until((StructuralCondition) null));
        assertNullCondition(() -> collection.until(
                (StructuralCondition.ExplainedCondition) null));

    }

    private static void assertNullSource(Executable action) {
        assertEquals("source must not be null",
                assertThrows(NullPointerException.class, action).getMessage());
    }

    private static void assertNullCondition(Executable action) {
        assertEquals("condition must not be null",
                assertThrows(NullPointerException.class, action).getMessage());
    }
}
