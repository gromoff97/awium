package io.github.gromoff97.awium;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class CompositionDiagnosticsTest {

    @Test
    void quantifiersRetainTheNamedCheckInFailureDiagnostics() {
        String description = "account is active";
        var mismatch = condition(description, ignored -> ConditionResult.unsatisfied());
        var match = condition(description, ignored -> ConditionResult.satisfied());
        var time = new FakeTime(0);
        var collection = await(() -> List.of("pending"))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var map = await(() -> Map.of("account", "pending"))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var nested = await(() -> List.of(List.of("pending")))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        var results = List.of(
                collection.tryUntil(CollectionConditions.all(mismatch)),
                collection.tryUntil(CollectionConditions.any(mismatch)),
                collection.tryUntil(CollectionConditions.none(match)),
                map.tryUntil(MapConditions.allEntries(mismatch)),
                map.tryUntil(MapConditions.hasEntry(mismatch)),
                map.tryUntil(MapConditions.hasNoEntry(match)),
                map.tryUntil(MapConditions.allKeys(mismatch)),
                map.tryUntil(MapConditions.anyKey(mismatch)),
                map.tryUntil(MapConditions.noKey(match)),
                map.tryUntil(MapConditions.allValues(mismatch)),
                map.tryUntil(MapConditions.anyValue(mismatch)),
                map.tryUntil(MapConditions.noValue(match)),
                nested.tryUntil(CollectionConditions.all(CollectionConditions.any(mismatch))),
                collection.tryUntil(isNotNull, CollectionConditions.all(mismatch)));

        for (var result : results) {
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(TIMEOUT, failure.reason());
            assertTrue(failure.message().contains(description), failure.message());
            var last = result.attempts().getLast();
            var failed = last.progress() == null ? last.condition() : last.progress().activeCondition();
            assertTrue(failed.description().endsWith(": " + description), failed.description());
        }
    }

    @Test
    void missingOptionalReportsPresenceBeforeEitherNestedConditionFamily() {
        List<Optional<String>> observations = asList(null, Optional.empty());
        for (var actual : observations) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            var selected = hasValue(equalTo("Bob"));
            var typed = hasValue(instanceOf(Integer.class));
            var selectedResult = waiting.tryUntil(selected);
            var typedResult = waiting.tryUntil(typed);

            for (var result : List.of(selectedResult, typedResult)) {
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                var condition = result.attempts().getLast().condition();
                assertEquals(TIMEOUT, failure.reason());
                assertEquals(present.description(), condition.description());
                assertNull(condition.reference());
                assertFalse(failure.message().contains("Expected:"));
            }
            var failure = assertInstanceOf(AwaitResult.Failed.class, selectedResult).failure();
            assertEquals(failure.message(), assertInstanceOf(AwaitResult.Failed.class, typedResult).failure().message());
            assertEquals(failure.message(), assertFailure(TIMEOUT, () -> waiting.until(selected)).getMessage());
            assertEquals(failure.message(), assertFailure(TIMEOUT, () -> waiting.until(typed)).getMessage());
        }
    }

    @Test
    void optionalHistorySwitchesFromPresenceToTheNestedExpectation() {
        var observations = List.of(Optional.<String>empty(), Optional.of("Alice"), Optional.of("Bob")).iterator();
        var time = new FakeTime(0);
        var nested = equalTo("Bob");
        var selected = hasValue(nested);
        var result = await(observations::next).usingTime(time, time).every(ofNanos(1)).tryUntil(selected);

        assertEquals("Bob", assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(3, result.totalAttempts());
        var missing = result.attempts().getFirst().condition();
        assertEquals(present.description(), missing.description());
        assertNull(missing.reference());
        for (var attempt : result.attempts().subList(1, 3)) {
            assertEquals(selected.description(), attempt.condition().description());
            assertEquals(nested.reference(), attempt.condition().reference());
        }
    }

    @Test
    void missingKeyAndNestedMismatchReportTheirOwnExpectedValues() {
        for (Map<String, String> actual : asList(null, Map.<String, String>of(), Map.of("customerId", "Alice"))) {
            var time = new FakeTime(0);
            var result = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).tryUntil(
                    valueFor("customerId", equalTo("Bob")));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Expected: " + (actual == null || actual.isEmpty() ? "customerId" : "Bob")), failure.message());
            assertEquals(3, result.attempts().size());
            assertEquals(3, result.totalAttempts());
        }
    }

    @Test
    void historyRetainsLookupAndNestedExpectationsAfterTheKeyAppears() {
        var observations = List.of(Map.<String, String>of(), Map.of("customerId", "Alice"), Map.of("customerId", "Bob"));
        int[] calls = {0};
        var time = new FakeTime(0);
        var result = await(() -> observations.get(calls[0]++)).usingTime(time, time).every(ofNanos(1)).tryUntil(
                valueFor("customerId", equalTo("Bob")));

        assertEquals("Bob", assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(3, result.attempts().size());
        var lookup = result.attempts().get(0);
        var nested = result.attempts().get(1);
        var success = result.attempts().get(2);
        assertEquals("map contains the expected key", lookup.condition().description());
        assertEquals("customerId", lookup.condition().reference().value());
        assertEquals("Bob", nested.condition().reference().value());
        assertEquals(nested.condition(), success.condition());
        for (var attempt : List.of(lookup, nested, success)) {
            assertNull(attempt.progress());
        }
    }

    @Test
    void missingKeyInsideCapturedKeepsLookupContextAndDoesNotCreateTheNestedSession() {
        var time = new FakeTime(0);
        Condition.MapValueCondition<Map<? super String, ?>, String, Condition.Reference<?>> selected = valueFor("customerId", conditionPerWait("nested", () -> {
            throw new AssertionError("nested factory must not run before extraction succeeds");
        }));
        var failure = assertFailure(TIMEOUT, () -> await(() -> Map.<String, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(selected, selected));
        assertTrue(failure.getMessage().contains("Expected: customerId"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Sequence (captured 0 of 2)"), failure.getMessage());
    }

    @Test
    void lookupKeyIsFormattedOnlyOnFailureInsideTheProtectedRenderer() {
        var time = new FakeTime(0);
        var formattingFailure = new IllegalStateException("key formatting failed");
        int[] renders = {0};
        Object key = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw formattingFailure;
            }
        };
        var selected = valueFor(key, equalTo("Bob"));
        assertEquals(0, renders[0]);
        var failure = assertFailure(TIMEOUT, () -> await(() -> Map.<Object, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(selected));
        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{formattingFailure}, failure.getSuppressed());
        assertEquals(1, renders[0]);
    }

    @Test
    void failedExtractionRetainsTheKeyAndDoesNotInitializeTheNestedCallback() {
        for (Throwable cause : List.of(new IllegalStateException("value failed"),
                new AssertionError("getter assertion"), new Throwable("getter throwable"))) {
            var entry = new java.util.AbstractMap.SimpleImmutableEntry<>("customerId", "Alice") {
                @Override public String getValue() { return throwUnchecked(cause); }
            };
            var actual = new ProbeContainers.EntryMap<String, String>(List.of(entry));
            var selected = valueFor("customerId", conditionPerWait("nested", () -> {
                throw new AssertionError("nested factory must not run before extraction succeeds");
            })).because("customer lookup must succeed");
            var waiting = await(() -> actual);

            var result = waiting.tryUntil(selected);
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(CONDITION_FAILED, failure.reason());
            assertSame(cause, failure.cause());
            assertTrue(failure.message().contains("Expected: customerId"), failure.message());
            assertTrue(failure.message().contains("Because: customer lookup must succeed"), failure.message());
            assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class, result.attempts().getFirst().outcome());
            assertEquals("customerId", result.attempts().getFirst().condition().reference().value());
            assertSame(cause, assertFailure(CONDITION_FAILED, () -> waiting.until(selected)).getCause());

            var captured = waiting.tryUntil(selected, selected);
            var capturedOutcome = assertInstanceOf(AttemptRecord.Outcome.ConditionFailed.class,
                    captured.attempts().getFirst().outcome());
            assertSame(cause, capturedOutcome.cause());
            var progress = captured.attempts().getFirst().progress();
            assertEquals(0, progress.capturedStages());
            assertEquals("customerId", progress.activeCondition().reference().value());
            assertEquals("customer lookup must succeed", progress.activeCondition().explanation());
        }
    }
}
