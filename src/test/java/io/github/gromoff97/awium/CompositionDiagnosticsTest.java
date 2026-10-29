package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.evaluated;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class CompositionDiagnosticsTest {

    @Test
    void quantifiersReportTheirScopeAndNamedCheckOnFailure() {
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

        assertQuantifierFailure(diagnostic(collection).until(CollectionConditions.all(mismatch)),
                "all collection elements match", "not all collection elements matched (checked 1)");
        assertQuantifierFailure(diagnostic(collection).until(CollectionConditions.any(mismatch)),
                "some collection elements match", "no collection elements matched (checked 1)");
        assertQuantifierFailure(diagnostic(collection).until(CollectionConditions.none(match)),
                "no collection elements match", "some collection elements matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.allEntries(mismatch)),
                "all map entries match", "not all map entries matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.hasEntry(mismatch)),
                "some map entries match", "no map entries matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.hasNoEntry(match)),
                "no map entries match", "some map entries matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.allKeys(mismatch)),
                "all map keys match", "not all map keys matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.anyKey(mismatch)),
                "some map keys match", "no map keys matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.noKey(match)),
                "no map keys match", "some map keys matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.allValues(mismatch)),
                "all map values match", "not all map values matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.anyValue(mismatch)),
                "some map values match", "no map values matched (checked 1)");
        assertQuantifierFailure(diagnostic(map).until(MapConditions.noValue(match)),
                "no map values match", "some map values matched (checked 1)");
        assertQuantifierFailure(diagnostic(nested).until(CollectionConditions.all(CollectionConditions.any(mismatch))),
                "all collection elements match: some collection elements match",
                "not all collection elements matched (checked 1): no collection elements matched (checked 1)");
        assertQuantifierFailure(diagnostic(collection).until(captured(isNotNull, CollectionConditions.all(mismatch))),
                "all collection elements match", "not all collection elements matched (checked 1)");
    }

    private static void assertQuantifierFailure(AwaitResult<?, ?> result, String scope, String mismatch) {
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(TIMEOUT, failure.reason());
        String description = scope + ": account is active";
        assertTrue(failure.message().contains(description), failure.message());
        assertTrue(failure.message().contains(mismatch), failure.message());
        var last = result.attempts().getLast().last();
        var condition = last.condition();
        assertEquals(description, condition.description());
        var evaluated = evaluated(last);
        assertEquals(mismatch, assertInstanceOf(AttemptStatus.Unsatisfied.class, evaluated.result()).mismatch());
    }

    @Test
    void missingOptionalReportsPresenceBeforeEitherNestedConditionFamily() {
        List<Optional<String>> observations = asList(null, Optional.empty());
        for (var actual : observations) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            var selected = hasValue(equalTo("Bob"));
            var typed = hasValue(instanceOf(Integer.class));
            var selectedResult = diagnostic(waiting).until(selected);
            var typedResult = diagnostic(waiting).until(typed);

            for (var result : List.of(selectedResult, typedResult)) {
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                var condition = result.attempts().getLast().last().condition();
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
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(selected);

        assertEquals("Bob", result.orThrow());
        assertEquals(3, result.attempts().size());
        var missing = result.attempts().getFirst().last().condition();
        assertEquals(present.description(), missing.description());
        assertNull(missing.reference());
        for (var attempt : result.attempts().subList(1, 3)) {
            assertEquals(selected.description(), attempt.last().condition().description());
            assertEquals(nested.reference(), attempt.last().condition().reference());
        }
    }

    @Test
    void missingKeyAndNestedMismatchReportTheirOwnExpectedValues() {
        for (Map<String, String> actual : asList(null, Map.<String, String>of(), Map.of("customerId", "Alice"))) {
            var time = new FakeTime(0);
            var result = tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(
                    valueFor("customerId", equalTo("Bob")));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Expected: " + (actual == null || actual.isEmpty() ? "customerId" : "Bob")), failure.message());
            assertEquals(1, result.attempts().size());
            assertEquals(3, result.attempts().getFirst().count());
        }
    }

    @Test
    void historyRetainsLookupAndNestedExpectationsAfterTheKeyAppears() {
        var observations = List.of(Map.<String, String>of(), Map.of("customerId", "Alice"), Map.of("customerId", "Bob"));
        int[] calls = {0};
        var time = new FakeTime(0);
        var result = tryAwait(() -> observations.get(calls[0]++)).usingTime(time, time).every(ofNanos(1)).until(
                valueFor("customerId", equalTo("Bob")));

        assertEquals("Bob", result.orThrow());
        assertEquals(3, result.attempts().size());
        var lookup = result.attempts().get(0).last();
        var nested = result.attempts().get(1).last();
        var success = result.attempts().get(2).last();
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
        Condition.MapValueCondition<Map<? super String, ? extends String>> selected = valueFor("customerId", conditionPerWait("nested", () -> {
            throw new AssertionError("nested factory must not run before extraction succeeds");
        }));
        var failure = assertFailure(TIMEOUT, () -> await(() -> Map.<String, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(captured(selected, selected)));
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

            var result = diagnostic(waiting).until(selected);
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(CONDITION_FAILED, failure.reason());
            assertSame(cause, failure.cause());
            assertTrue(failure.message().contains("Expected: customerId"), failure.message());
            assertTrue(failure.message().contains("Because: customer lookup must succeed"), failure.message());
            uncontrolled(AttemptStatus.Operation.CONDITION, result.attempts().getFirst().last().result());
            assertEquals("customerId", result.attempts().getFirst().last().condition().reference().value());
            assertSame(cause, assertFailure(CONDITION_FAILED, () -> waiting.until(selected)).getCause());

            var captured = diagnostic(waiting).until(captured(selected, selected));
            var capturedOutcome = uncontrolled(AttemptStatus.Operation.CONDITION, captured.attempts().getFirst().last().result());
            assertSame(cause, capturedOutcome.cause());
            var progress = captured.attempts().getFirst().last().progress();
            assertEquals(0, progress.capturedStages());
            assertEquals("customerId", captured.attempts().getFirst().last().condition().reference().value());
            assertEquals("customer lookup must succeed", captured.attempts().getFirst().last().condition().explanation());
        }
    }
}
