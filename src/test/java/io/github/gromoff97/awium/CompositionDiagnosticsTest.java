package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;
import io.github.gromoff97.awium.results.AwaitResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class CompositionDiagnosticsTest {

    @Test
    void missingKeyAndNestedMismatchReportTheirOwnExpectedValues() {
        for (Map<String, String> actual : List.of(Map.<String, String>of(), Map.of("customerId", "Alice"))) {
            var time = new FakeTime(0);
            var result = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                    .tryUntil(valueFor("customerId", equalTo("Bob")).because("customer is required"));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertInstanceOf(AwaitTimeoutException.class, failure);
            assertTrue(failure.getMessage().contains("Expected: " + (actual.isEmpty() ? "customerId" : "Bob")), failure.getMessage());
            assertTrue(failure.getMessage().contains("Importance: customer is required"), failure.getMessage());
            assertEquals(1, result.attempts().size());
            assertEquals(3, result.totalAttempts());
        }
    }

    @Test
    void missingKeyInsideCapturedKeepsLookupContextAndDoesNotCreateTheNestedSession() {
        var time = new FakeTime(0);
        Condition<Map<String, String>, String> selected = valueFor("customerId", conditionFactory("nested", () -> {
            throw new AssertionError("nested factory must not run before extraction succeeds");
        }));
        var failure = assertThrows(AwaitTimeoutException.class, () -> await(() -> Map.<String, String>of())
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
        Condition<Map<Object, String>, String> selected = valueFor(key, equalTo("Bob"));
        assertEquals(0, renders[0]);
        var failure = assertThrows(AwaitUnhandledException.class, () -> await(() -> Map.<Object, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(selected));
        assertSame(formattingFailure, failure.getCause());
        assertEquals(1, renders[0]);
    }
}
