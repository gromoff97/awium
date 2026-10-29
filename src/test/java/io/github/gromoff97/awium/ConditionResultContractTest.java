package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptStatus.Satisfied;
import io.github.gromoff97.awium.result.AttemptStatus.Unsatisfied;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.*;
import static io.github.gromoff97.awium.Conditions.captured;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionResultContractTest {

    private record User(int id) {}

    @Test
    void customChecksUseTheCallbacksStatusAndPreserveTheObservation() {
        CheckedFunction<User, ConditionResult> status = user -> user.id() > 0
                ? satisfied() : unsatisfied("id must be positive");
        Callable<CheckedFunction<User, ConditionResult>> factory = () -> status;
        var direct = Conditions.condition("positive id", status).because("user must be saved");
        var lazy = Conditions.conditionPerWait("positive id", factory).because("user must be saved");
        var actual = new User(42);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10));

        for (var check : List.of(direct, lazy)) {
            var value = waiting.until(check);
            assertEquals(42, value.id());
            assertSame(actual, value);
            AwaitResult<User, User> result = diagnostic(waiting).until(check);
            assertSame(actual, result.orThrow());
        }
        List<User> values = waiting.until(captured(direct, lazy));
        for (User value : values) assertSame(actual, value);
        assertSame(actual, await(() -> Optional.of(actual)).until(OptionalConditions.hasValue(direct)));
        assertEquals(42, await(() -> List.of(actual)).until(CollectionConditions.singleElement(lazy)).id());
        assertEquals(42, await(() -> Map.of("user", actual)).until(MapConditions.valueFor("user", direct)).id());

        for (var check : List.of(direct, lazy)) {
            var failed = tryAwait(() -> new User(0)).usingTime(time, time).upTo(ofNanos(1)).until(check);
            var failure = assertInstanceOf(AwaitResult.Failed.class, failed).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("id must be positive"), failure.message());
            assertTrue(failure.message().contains("Because: user must be saved"), failure.message());
        }
    }

    @Test
    void satisfactionWithoutAPayloadPreservesConcreteAndSelectedObservations() {
        ConditionResult signal = satisfied();
        assertInstanceOf(Satisfied.class, signal);
        assertNull(signal.cause());
        var actual = new User(42);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(42, waiting.until(Conditions.condition("ready", ignored -> satisfied())).id());
        AwaitResult<User, User> result = diagnostic(waiting).until(Conditions.condition("ready", ignored -> satisfied()));
        switch (result) {
            case AwaitResult.Satisfied(var history, var user) -> assertSame(actual, user);
            case AwaitResult.Failed(var history, var failure) -> org.junit.jupiter.api.Assertions.fail(failure.message());
        }
        assertSame(actual, await(() -> java.util.Optional.of(actual)).usingTime(time, time)
                .until(OptionalConditions.hasValue(Conditions.condition("present", ignored -> satisfied()))));
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(CollectionConditions.singleElement(Conditions.condition("single", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(CollectionConditions.firstElement(Conditions.condition("first", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.Map.of("user", actual)).usingTime(time, time)
                .until(MapConditions.valueFor("user", Conditions.condition("mapped", ignored -> satisfied()))).id());
        User absent = null;
        assertNull(await(() -> absent).usingTime(time, time).until(Conditions.condition("null is valid", ignored -> satisfied())));
    }

    @Test
    void conditionsCanFailWithoutDuplicatingTheirDescription() {
        var time = new FakeTime(0);
        AwaitResult<String, String> result = tryAwait(() -> "pending")
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                .until(Conditions.condition("status is ready", value -> unsatisfied()));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("Condition: status is ready"));
        assertTrue(failure.message().contains("Actual: pending"));
        assertFalse(failure.message().contains("Mismatch:"));
    }

    @Test
    void unsatisfiedAcceptsOptionalDiagnostics() {
        var plain = unsatisfied();
        assertEquals(new Unsatisfied(null, null), plain);
        assertEquals(plain, unsatisfied(null));
        assertEquals(plain, unsatisfied(null, null));
        var cause = new IllegalStateException("details");
        var detailed = unsatisfied("not ready", cause);
        assertEquals("not ready", detailed.mismatch());
        assertSame(cause, detailed.cause());
        assertSame(cause, unsatisfied(null, cause).cause());
        assertEquals(new Unsatisfied("failed", null), unsatisfied("failed", null));
    }

}
