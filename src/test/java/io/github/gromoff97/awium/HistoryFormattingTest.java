package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.isNull;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class HistoryFormattingTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void textValuesExposeNullTextWhitespaceAndControlCharacters() {
        Object[][] samples = {
            {null, "null"}, {"null", "\"null\""}, {"", "\"\""}, {" ", "\" \""},
            {"ready\n", "\"ready\\n\""}, {"ready\r\n", "\"ready\\r\\n\""},
            {"a\tb\b\f\u0000\u007f", "\"a\\tb\\b\\f\\u0000\\u007f\""},
            {"\"\\", "\"\\\"\\\\\""}, {'\'', "'\\''"},
            {new StringBuilder("готово 👋"), "\"готово 👋\""}
        };
        for (Object[] sample : samples) {
            var result = tryAwait(() -> sample[0]).until(equalTo(sample[0]));
            String report = result.toString();
            String expected = (String) sample[1];
            assertThat(report).contains("Result: " + expected + "\n", "Expected: " + expected + "\n")
                    .endsWith("Observed now: " + expected);
        }
    }

    @Test
    void historyRendersTheDefinitionOnceFollowedByNumberedStates() {
        var observations = List.of(1, 1, 1, 4).iterator();
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1))
                .until(equalTo(4).because("worker must finish"));

        assertEquals("""
                Condition: value equals expected
                    Expected: 4
                    Because: worker must finish

                Through #3: unsatisfied
                    Mismatch: value was not equal
                    Observed now: 1

                Through #4: satisfied
                    Observed now: 4""", result.conditions().getFirst().toString());
        assertFalse(observations.hasNext());
        String report = result.toString();
        assertEquals(1, report.lines().filter(line -> line.startsWith("Condition ")).count());
        assertThat(report).startsWith("Wait satisfied\n    Result: 4\n\nCondition 1:").contains("Through #3", "Through #4");
    }

    @Test
    void conditionHistoriesRenderSharedOperandsOnceAndAttemptsContainOnlyState() {
        int[] renders = {0};
        List<String> expected = new ArrayList<>(List.of("ready")) {
            @Override public String toString() { renders[0]++; return super.toString(); }
        };
        var values = List.of(List.<String>of(), List.of("ready")).iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1)).until(equalTo(expected));

        assertEquals(0, renders[0]);
        String report = result.toString();
        assertEquals(1, renders[0]);
        assertThat(report).contains(": unsatisfied", ": satisfied");
        String last = attempts(result).getLast().toString();
        assertEquals(1, renders[0]);
        assertEquals("Result: satisfied\n    Observed now: [ready]", last);
    }

    @Test
    void capturedHistoryLabelsEachConditionAndItsChanges() {
        var values = List.of("created", "pending", "pending", "paid").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(equalTo("created"), equalTo("paid").because("payment accepted"))
                        .because("order lifecycle"));

        String report = result.toString();
        assertEquals(2, report.lines().filter(line -> line.startsWith("Condition ")).count());
        assertThat(report).contains(
                "Condition 1:",
                "Condition 2:",
                "Expected: \"created\"",
                "Expected: \"paid\"",
                "Because: payment accepted",
                "Through #3",
                "Observed now: \"pending\"");
    }

    @Test
    void returningToAnEarlierConditionRepeatsItsDefinitionInTheReport() {
        var ready = equalTo("ready");
        var values = List.of("ready", "retry", "ready").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(ready, equalTo("retry"), ready));

        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.startsWith("Condition ")).count());
        assertEquals(2, report.lines().filter(line -> line.contains("Expected: \"ready\"")).count());
        assertEquals(List.of("ready", "retry", "ready"), result.orThrow());
    }

    @Test
    void aReturnedNullIsDifferentFromAnUnavailableObservation() {
        var returnedNull = tryAwait((Callable<String>) () -> null)
                .usingTime(time, time).until(isNull);
        var cause = new IllegalStateException("source unavailable");
        var sourceFailed = tryAwait((Callable<String>) () -> { throw cause; })
                .usingTime(time, time).until(isNull);

        assertThat(attempts(returnedNull).getFirst().toString()).contains("Observed now: null");
        String failed = attempts(sourceFailed).getFirst().toString();
        assertThat(failed).doesNotContain("Observed now:").contains(
                "Observed: unavailable",
                "Result: failed (SOURCE)",
                "Cause: java.lang.IllegalStateException",
                "java.lang.IllegalStateException: source unavailable");
        assertSame(cause, failure(sourceFailed).cause());
    }

    @Test
    void valueRenderingIsDeferredAndFailuresCannotReplaceTheWaitOutcome() {
        int[] renders = {0};
        Object observed = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw new IllegalArgumentException("unprintable");
            }
        };
        var result = tryAwait(() -> observed).usingTime(time, time)
                .until(condition("ready", actual -> satisfied(actual)));

        assertEquals(0, renders[0]);
        assertSame(observed, result.orThrow());
        String rendered = attempts(result).getFirst().toString();
        assertEquals(1, renders[0]);
        assertThat(rendered).contains(
                "Observed now: <unprintable>",
                "Rendering failure: java.lang.IllegalArgumentException");
        String individual = attempts(result).getFirst().toString();
        assertEquals(2, renders[0]);
        assertThat(individual).contains("Result: satisfied", "Observed now: <unprintable>");
        assertSame(observed, result.orThrow());
    }

    @Test
    void multilineReasonsAndArrayValuesRemainReadable() {
        var result = tryAwait(() -> new int[]{1, 2}).usingTime(time, time)
                .until(equalTo(new int[]{1, 2}).because("first line\r\nsecond line\r\n"));

        String rendered = result.conditions().getFirst().toString();
        assertThat(rendered).contains(
                "Expected: [1, 2]",
                "    Because:\n        first line\n        second line\n\n\nThrough #1: satisfied",
                "Observed now: [1, 2]");
    }

    @Test
    void repeatedObservationIsRenderedOnceWithoutComparingUserValues() {
        int[] renders = {0};
        Object observed = new Object() {
            @Override public String toString() { return "render " + ++renders[0]; }
            @Override public boolean equals(Object other) { throw new AssertionError("unexpected equals"); }
            @Override public int hashCode() { throw new AssertionError("unexpected hashCode"); }
        };
        var outcomes = List.of(unsatisfied(), unsatisfied(), satisfied(observed)).iterator();
        var result = tryAwait(() -> observed).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", actual -> outcomes.next()));

        assertSame(observed, result.orThrow());
        assertEquals(0, renders[0]);
        var attempt = attempts(result).getFirst();
        assertEquals(2L, result.conditions().getFirst().attempts().firstKey());
        String rendered = attempt.toString();
        assertEquals(1, renders[0]);
        assertThat(rendered).contains("Observed now: render 1");
        assertSame(observed, observation(attempt));
    }

    @Test
    void latestEqualObservationIsRetainedAndRenderingDoesNotCompareAgain() {
        class Observation {
            int renders;
            int comparisons;
            @Override public String toString() { renders++; return "pending"; }
            @Override public boolean equals(Object other) { comparisons++; return other instanceof Observation; }
            @Override public int hashCode() { throw new AssertionError("unexpected hashCode"); }
        }
        var first = new Observation();
        var last = new Observation();
        var values = List.of(first, last, last).iterator();
        var outcomes = List.of(unsatisfied(), unsatisfied(), satisfied(last)).iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", actual -> outcomes.next()));

        assertSame(last, result.orThrow());
        assertEquals(0, first.renders + last.renders);
        assertEquals(1, first.comparisons + last.comparisons);
        var attempt = attempts(result).getFirst();
        String rendered = attempt.toString();
        assertThat(rendered).contains("Observed now: pending");
        assertEquals(1, rendered.lines().filter(line -> line.contains("Observed now")).count());
        assertEquals(0, first.renders);
        assertEquals(1, last.renders);
        assertEquals(1, first.comparisons + last.comparisons);
        assertEquals(2L, result.conditions().getFirst().attempts().firstKey());
        assertSame(last, observation(attempt));
    }

    @Test
    void diagnosticResultsDoNotDelegateToUncheckedValueOrThrowableRenderers() {
        Object observed = new Object() {
            @Override public String toString() { throw new IllegalStateException("value renderer failed"); }
        };
        var successful = tryAwait(() -> observed).usingTime(time, time)
                .until(condition("ready", actual -> satisfied(actual)));
        var cause = new IllegalArgumentException("original cause") {
            @Override public String toString() { throw new IllegalStateException("cause renderer failed"); }
        };
        var failed = tryAwait((Callable<String>) () -> { throw cause; })
                .usingTime(time, time).until(isNotNull);

        String successText = successful.toString();
        String failureText = failed.toString();
        assertThat(successText).startsWith("Wait satisfied\n    Result: <unprintable>").contains("Through #1");
        assertThat(failureText).startsWith("Source retrieval failed").contains("original cause", "Through #1");
        String individual = attempts(failed).getFirst().toString();
        assertThat(individual).contains("Result: failed (SOURCE)", ": original cause");
        assertThat(unsatisfied("pending", cause).toString()).contains("original cause");
        var failure = failure(failed);
        assertEquals(failure.message(), failure.toString());
        assertSame(observed, successful.orThrow());
        assertSame(cause, failure(failed).cause());
    }

    @Test
    void selectedResultRenderingFailureIsReportedEvenWhenTheSourceRendersNormally() {
        int[] renders = {0};
        Object selected = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw new IllegalStateException("selected value cannot be printed");
            }
        };
        var source = new ArrayList<>(List.of(selected)) {
            @Override public String toString() { return "readable source"; }
        };
        var result = tryAwait(() -> source).until(singleElement);

        assertSame(selected, result.orThrow());
        assertEquals(0, renders[0]);
        String report = result.toString();
        assertThat(report).contains(
                "Rendering failure: java.lang.IllegalStateException",
                "Observed now: readable source");
        assertEquals(1, renders[0]);
    }

    @Test
    void outcomeRenderingKeepsItsCategoryWhenTheCauseMessageFails() {
        assertThat(unsatisfied("pending").toString()).contains("pending");
        var cause = new IllegalStateException() {
            @Override public String getMessage() { throw new AssertionError("message unavailable"); }
        };
        var mismatch = unsatisfied("pending", cause);
        var failure = new FailedAfterObservation<>("actual", CONDITION, cause, null);
        assertThat(mismatch.toString()).startsWith("Unsatisfied[");
        assertThat(failure.toString()).contains("Result: failed (CONDITION)");
        assertSame(cause, mismatch.cause());
        assertSame(cause, failure.cause());
        for (String rendered : List.of(mismatch.toString(), failure.toString())) {
            assertThat(rendered).contains(cause.getClass().getName(), "<unprintable>");
        }
    }

    @Test
    void liveObservationLabelsCannotBeMistakenForSnapshotsOfEarlierSizes() {
        var values = new ArrayList<Integer>();
        var updates = List.<Runnable>of(() -> values.add(1), () -> {}, () -> values.add(3), () -> {}, () -> values.add(5)).iterator();
        var result = tryAwait(() -> {
            updates.next().run();
            return values;
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(hasSize(4));
        values.add(99);

        assertEquals(3, attempts(result).size());
        for (int i = 0; i < attempts(result).size(); i++) {
            var attempt = attempts(result).get(i);
            String rendered = attempt.toString();
            assertThat(rendered).contains("Mismatch: collection size was " + (i + 1), "Observed now: [1, 3, 5, 99]");
            assertSame(values, observation(attempt));
        }
        String failure = failure(result).message();
        assertThat(failure).contains("Actual: [1, 3, 5]");
    }
}
