package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.Diagnostic;
import io.github.gromoff97.awium.support.FakeTime;
import io.github.gromoff97.awium.support.TestCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class StructuredDiagnosticsTest {

    @Test
    void aConditionReportsTheMeasuredValueAfterTheObjectChanges() {
        var time = new FakeTime(0);
        var calls = new AtomicInteger();
        var values = new ArrayList<>(List.of("first", "second"));
        var condition = new Condition<List<String>, List<String>>() {
            @Override public String description() { return "three elements"; }
            @Override public ConditionResult<List<String>> probe(List<String> actual) {
                calls.incrementAndGet();
                var measured = new Diagnostic("collection size did not match",
                        new Reference<>("Expected size", 3), new Reference<>("Actual size", actual.size()));
                actual.clear();
                time.advanceNanos(2);
                return unsatisfied(measured);
            }
        }.because("all entries must arrive");

        var result = diagnostic(timedAwait(() -> values, time).upTo(ofNanos(1))).until(condition);

        assertThat(failure(TIMEOUT, result).message()).contains("Condition: three elements",
                "Because: all entries must arrive", "Expected size: 3", "Actual size: 2", "Actual: []");
        assertEquals(1, calls.get());
        assertEquals(2, attempts(result).getFirst().diagnostic().fields().getLast().value());
    }

    @Test
    void aLateSuccessRetainsItsDiagnosticFields() {
        var time = new FakeTime(0);
        var measured = new Diagnostic(null, new Reference<>("Received status", "ready"));
        var condition = new TestCondition<String, String>("ready", null, actual -> {
            time.advanceNanos(2);
            return satisfied(actual, measured);
        });

        var result = diagnostic(timedAwait(() -> "ready", time).upTo(ofNanos(1))).until(condition);

        assertThat(failure(TIMEOUT, result).message()).contains("Received status: \"ready\"");
        assertSame(measured, assertInstanceOf(SatisfiedObservation.class, attempts(result).getFirst()).diagnostic());
    }

    @Test
    void historyKeepsFieldChangesEvenWhenTheObservationAndMessageStayTheSame() {
        var time = new FakeTime(0);
        var progress = List.of(1, 1, 2, 2, 1, 3).iterator();
        var condition = new TestCondition<String, String>("progress", null, actual -> {
            int value = progress.next();
            var measured = new Diagnostic(null, new Reference<>("Progress", value));
            return value == 3 ? satisfied(actual, measured) : unsatisfied(measured.withMismatch("progress is incomplete"));
        });

        var result = diagnostic(timedAwait(() -> "same object", time).every(ofNanos(1))).until(condition);
        var changes = result.conditions().getFirst().attempts();

        assertThat(changes.keySet()).containsExactly(2L, 4L, 5L, 6L);
        assertEquals(List.of(1, 2, 1, 3), changes.values().stream()
                .map(attempt -> attempt.diagnostic().fields().getFirst().value()).toList());
        assertThat(result.toString()).contains("Progress: 1", "Progress: 2", "Progress: 3");
    }

    @Test
    void anUnreadableFieldDoesNotReplaceTheOriginalCause() {
        var time = new FakeTime(0);
        var original = new AssertionError("not ready");
        var rendering = new IllegalStateException("field failed");
        var unreadable = new Object() {
            @Override public String toString() { throw rendering; }
        };
        var condition = new TestCondition<String, String>("ready", null, actual -> {
            time.advanceNanos(2);
            return unsatisfied(new Diagnostic("pending", new Reference<>("Details", unreadable)), original);
        });

        var result = diagnostic(timedAwait(() -> "pending", time).upTo(ofNanos(1))).until(condition);
        var failed = failure(TIMEOUT, result);

        assertSame(original, failed.cause());
        assertThat(failed.suppressed()).containsExactly(rendering);
        assertThat(failed.message()).contains("Details: <unprintable>", "Mismatch: pending", "Timing:");
    }

    @Test
    void anOnTimeSuccessDoesNotRenderDiagnosticValues() {
        var detail = new Object() {
            @Override public String toString() { throw new AssertionError("must not render"); }
        };
        var condition = new TestCondition<String, String>("ready", null,
                actual -> satisfied(actual, new Diagnostic(null, new Reference<>("Details", detail))));

        assertEquals("ready", timedAwait(() -> "ready", new FakeTime(0)).until(condition));
    }

    @Test
    void aConditionValuedFieldDescribesTheCheckWithoutRunningIt() {
        var time = new FakeTime(0);
        var nested = new TestCondition<String, String>("nested check", new Reference<>("Expected", 3), actual -> {
            throw new AssertionError("diagnostics must not run a nested check");
        }).because("nested reason");
        var condition = new TestCondition<String, String>("ready", null, actual -> {
            time.advanceNanos(2);
            return unsatisfied(new Diagnostic("nested check is pending", new Reference<>("Nested", nested)));
        });

        var result = diagnostic(timedAwait(() -> "pending", time).upTo(ofNanos(1))).until(condition);

        assertThat(failure(TIMEOUT, result).message()).contains(
                "    Nested: nested check\n        Expected: 3\n        Because: nested reason");
    }
}
