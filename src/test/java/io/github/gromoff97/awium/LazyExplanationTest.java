package io.github.gromoff97.awium;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class LazyExplanationTest {

    @Test
    void successfulWaitsNeverEvaluateExplanationsAcrossConditionFamilies() {
        Supplier<String> forbidden = () -> { throw new AssertionError("explanation was not needed"); };

        assertEquals(5, await(() -> "ready").until(isNotNull.because(forbidden)).length());
        assertEquals(5, await(() -> "ready").until(equalTo("ready").because(forbidden)).length());
        assertEquals(5, awaitOptional(() -> Optional.of("ready")).until(present.because(forbidden)).length());
        assertEquals(5, awaitCollection(() -> List.of("ready")).until(single.because(forbidden)).length());
        assertEquals(5, awaitMap(() -> Map.of("key", "ready")).until(valueFor("key").because(forbidden)).length());
        Object observed = "ready";
        assertEquals(5, await(() -> observed).until(instanceOf(String.class).because(forbidden)).length());
        assertEquals(5, awaitOptional(() -> Optional.of("ready")).until(
                hasValue(isNotNull.because(forbidden)).because(forbidden)).length());
        assertEquals(5, awaitCollection(() -> List.of("ready")).until(
                single(isNotNull.because(forbidden)).because(forbidden)).length());
        assertEquals(5, awaitMap(() -> Map.of("key", "ready")).until(
                valueFor("key", isNotNull.because(forbidden)).because(forbidden)).length());
        assertEquals(5, awaitMap(() -> Map.of("key", "ready")).until(
                singleEntry(isNotNull.because(forbidden)).because(forbidden)).getValue().length());
        assertInstanceOf(AwaitResult.Satisfied.class,
                await(() -> "ready").tryUntil(equalTo("ready").because(forbidden)));
    }

    @Test
    void nestedFailuresInheritLazyTextAndOuterReasonsTakePrecedence() {
        var time = new FakeTime(0);
        var optionals = awaitOptional(() -> Optional.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var collections = awaitCollection(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var maps = awaitMap(() -> Map.of("key", "pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        List<Function<Supplier<String>, AwaitResult<?, ?>>> waits = List.of(
                reason -> optionals.tryUntil(hasValue(equalTo("ready").because(reason))),
                reason -> collections.tryUntil(single(equalTo("ready").because(reason))),
                reason -> maps.tryUntil(valueFor("key", equalTo("ready").because(reason))),
                reason -> maps.tryUntil(valueFor("missing", equalTo("ready").because(reason))),
                reason -> maps.tryUntil(singleEntry(matches(value -> false).because(reason))),
                reason -> optionals.tryUntil(hasValue(instanceOf(Integer.class).because(reason))));

        for (var wait : waits) {
            int[] renderings = {0};
            var result = wait.apply(() -> { renderings[0]++; return "inner"; });
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertTrue(failure.message().contains("Because: inner"));
            assertEquals(1, renderings[0]);
        }
        int[] inner = {0};
        int[] outer = {0};
        var nested = equalTo("ready").because(() -> { inner[0]++; return "inner"; });
        var result = optionals.tryUntil(hasValue(nested).because(() -> { outer[0]++; return "outer"; }));

        assertTrue(assertInstanceOf(AwaitResult.Failed.class, result).failure().message().contains("Because: outer"));
        assertEquals(0, inner[0]);
        assertEquals(1, outer[0]);
    }

    @Test
    void successfulSequencesAndPersistenceLeaveExplanationsUnevaluated() {
        Supplier<String> forbidden = () -> { throw new AssertionError("explanation was not needed"); };
        var time = new FakeTime(0);
        var values = await(() -> "ready").usingTime(time, time).every(ofNanos(1)).persisting(ofNanos(2))
                .until(equalTo("ready").because(forbidden), equalTo("ready").because(forbidden));

        assertEquals(5, values.getFirst().length());
        assertEquals(5, values.getLast().length());
    }

    @Test
    void failureFreezesLazyTextAfterTheFinalAttempt() {
        int[] reads = {0};
        int[] renderings = {0};
        var condition = equalTo("ready").because(() -> "reads=" + reads[0] + ", rendering=" + ++renderings[0]);
        var result = waiting(() -> { reads[0]++; return "pending"; }).tryUntil(condition);
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        String message = failure.message();

        assertEquals(1, renderings[0]);
        assertTrue(message.contains("Because: reads=" + reads[0] + ", rendering=1"));
        assertEquals("reads=" + reads[0] + ", rendering=2", result.attempts().getLast().condition().explanation());
        assertEquals(message, failure.message());
        assertEquals(2, renderings[0]);
    }

    @Test
    void reuseAndReplacementPreserveIndependentConditions() {
        int[] renderings = {0};
        var original = equalTo("ready");
        var lazy = original.because(() -> "rendering " + ++renderings[0]);
        var fixed = lazy.because("fixed");
        var waiting = waiting(() -> "pending");

        var first = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(lazy)).failure();
        var replaced = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(fixed)).failure();
        var unexplained = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(original)).failure();
        assertEquals(1, renderings[0]);
        assertTrue(first.message().contains("Because: rendering 1"));
        assertTrue(replaced.message().contains("Because: fixed"));
        assertFalse(unexplained.message().contains("Because:"));

        var second = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(lazy)).failure();
        assertTrue(second.message().contains("Because: rendering 2"));
        assertEquals(2, renderings[0]);
        var error = assertThrows(AwaitAssertionError.class, () -> waiting.until(lazy));
        assertTrue(error.getMessage().contains("Because: rendering 3"));
        assertEquals(3, renderings[0]);
    }

    @Test
    void explanationFailuresCannotReplaceThePrimaryFailure() {
        var diagnostic = new AssertionError("diagnostic");
        var primary = new IllegalArgumentException("primary");
        var result = waiting(() -> "pending").tryUntil(condition("failed check", value -> { throw primary; })
                .because(() -> { throw diagnostic; }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

        assertEquals(AwaitFailure.Reason.CONDITION_FAILED, failure.reason());
        assertSame(primary, failure.cause());
        assertEquals(List.of(diagnostic), failure.suppressed());
    }

    @Test
    void onlyTheActiveSequenceStageProducesItsExplanation() {
        int[] inactive = {0};
        int[] active = {0};
        var result = waiting(() -> "pending").tryUntil(
                equalTo("pending").because(() -> { inactive[0]++; return "first"; }),
                equalTo("ready").because(() -> { active[0]++; return "second"; }));

        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertTrue(failure.message().contains("Because: second"));
        assertEquals(0, inactive[0]);
        assertEquals(1, active[0]);
    }

    @Test
    void invalidLazyTextIsReportedOnlyWhenDiagnosticsNeedIt() {
        Supplier<String> missing = null;
        assertThrows(NullPointerException.class, () -> equalTo("ready").because(missing));
        for (String invalid : Arrays.asList(null, "", " \n ")) {
            var condition = equalTo("ready").because(() -> invalid);
            assertEquals(5, await(() -> "ready").until(condition).length());
            var failure = assertInstanceOf(AwaitResult.Failed.class, waiting(() -> "pending").tryUntil(condition)).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertNull(failure.cause());
            assertEquals(1, failure.suppressed().size());
            assertEquals(invalid == null ? NullPointerException.class : IllegalArgumentException.class,
                    failure.suppressed().getFirst().getClass());
        }
    }

    @Test
    void diagnosticSupplierCannotConsumeAnExistingInterruptFlag() {
        try {
            Thread.currentThread().interrupt();
            var result = waiting(() -> "ready").tryUntil(equalTo("ready").because(() -> {
                assertTrue(Thread.interrupted());
                return "cancelled";
            }));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();

            assertEquals(AwaitFailure.Reason.INTERRUPTED, failure.reason());
            assertEquals(List.of(), failure.suppressed());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static ValueAwait<String> waiting(Callable<String> source) {
        var time = new FakeTime(0);
        return await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
    }
}
