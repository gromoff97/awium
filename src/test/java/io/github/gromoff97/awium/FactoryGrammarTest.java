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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullSource(() -> await((Source<Object>) null));
        assertNullSource(() -> await((OptionalSource<Object>) null));
        assertNullSource(() -> await(
                (CollectionSource<Collection<Object>>) null));
        assertNullSource(() -> await(
                (MapSource<Map<Object, Object>>) null));
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        AtomicInteger calls = new AtomicInteger();
        Await<String> initial = await(() -> {
            calls.incrementAndGet();
            return "value";
        });

        Await<String> slow = initial.every(Duration.ofSeconds(20));
        Await<String> repaired = slow
                .upTo(Duration.ofSeconds(10))
                .every(Duration.ofMillis(1))
                .upTo(Duration.ofSeconds(1))
                .stableFor(Duration.ofSeconds(2))
                .stableFor(Duration.ZERO);

        assertEquals("value", repaired.until(isNotNull));
        assertThrows(AwaitConfigurationConflictException.class,
                () -> slow.until(isNotNull));
        assertEquals(1, calls.get());
    }

    @Test
    void everyTerminalOverloadValidatesTheFinalConfigurationPair() {
        AtomicInteger sourceCalls = new AtomicInteger();
        Await<String> object = await(
                (Source<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return "value";
                }).every(Duration.ofSeconds(20));
        OptionalAwait<String> optional = await(
                (OptionalSource<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return Optional.of("value");
                }).every(Duration.ofSeconds(20));
        StructuralAwait<List<String>> collection =
                await((CollectionSource<List<String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return List.of("value");
                        }).every(Duration.ofSeconds(20));
        StructuralAwait<Map<String, String>> map =
                await((MapSource<Map<String, String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return Map.of("key", "value");
                        }).every(Duration.ofSeconds(20));
        Condition<String, String> selecting = condition(
                "selecting", Evaluation::satisfied);

        List<Executable> terminals = List.of(
                () -> object.until(isNotNull),
                () -> object.until(
                        isNotNull.because("preserving")),
                () -> object.until(selecting),
                () -> object.until(selecting.because("selecting")),
                () -> optional.until(present),
                () -> optional.until(
                        present.because("present")),
                () -> collection.until(nonEmpty),
                () -> collection.until(
                        nonEmpty.because("collection")),
                () -> map.until(nonEmpty),
                () -> map.until(nonEmpty.because("map")));

        for (Executable terminal : terminals) {
            assertThrows(AwaitConfigurationConflictException.class, terminal);
        }
        assertEquals(0, sourceCalls.get());
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

        StructuralAwait<Map<String, String>> map =
                await((MapSource<Map<String, String>>) Map::of)
                        .every(Duration.ofSeconds(20));
        assertNullCondition(() -> map.until((StructuralCondition) null));
        assertNullCondition(() -> map.until((StructuralCondition.ExplainedCondition) null));
    }

    @Test
    void conditionExpressionPrecedesDeferredConfigurationValidation() {
        AtomicInteger conditionExpressions = new AtomicInteger();

        assertThrows(AwaitConfigurationConflictException.class, () -> await((Source<String>) () -> "value")
                .every(Duration.ofSeconds(20))
                .upTo(Duration.ofSeconds(10))
                .until(countingCondition(conditionExpressions)));

        assertEquals(1, conditionExpressions.get());
    }

    private static Condition<String, String> countingCondition(AtomicInteger calls) {
        calls.incrementAndGet();
        return condition("value", Evaluation::satisfied);
    }

    private static void assertNullSource(Executable action) {
        NullPointerException failure = assertThrows(NullPointerException.class, action);
        assertEquals("source must not be null", failure.getMessage());
    }

    private static void assertNullCondition(Executable action) {
        NullPointerException failure = assertThrows(NullPointerException.class, action);
        assertEquals("condition must not be null", failure.getMessage());
    }
}
