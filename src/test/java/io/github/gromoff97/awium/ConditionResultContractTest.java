package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.*;
import static io.github.gromoff97.awium.Conditions.captured;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.gromoff97.awium.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;

class ConditionResultContractTest {

    private final FakeTime time = new FakeTime(0);

    private record User(int id) {}

    @Test
    void customChecksUseTheCallbacksStatusAndPreserveTheObservation() {
        CheckedFunction<User, ConditionResult> status = user -> user.id() > 0
                ? satisfied() : unsatisfied("id must be positive");
        Callable<CheckedFunction<User, ConditionResult>> factory = () -> status;
        var direct = condition("positive id", status).because("user must be saved");
        var lazy = conditionPerWait("positive id", factory).because("user must be saved");
        var actual = new User(42);
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
        assertSame(actual, await(() -> Optional.of(actual)).until(hasValue(direct)));
        assertEquals(42, await(() -> List.of(actual)).until(singleElement(lazy)).id());
        assertEquals(42, await(() -> Map.of("user", actual)).until(valueFor("user", direct)).id());

        for (var check : List.of(direct, lazy)) {
            var failed = tryAwait(() -> new User(0)).usingTime(time, time).upTo(ofNanos(1)).until(check);
            var failure = failure(AwaitFailure.Reason.TIMEOUT, failed);
            assertThat(failure.message()).contains("id must be positive", "Because: user must be saved");
        }
    }

    @Test
    void satisfactionWithoutAPayloadPreservesConcreteAndSelectedObservations() {
        ConditionResult signal = satisfied();
        assertInstanceOf(Satisfied.class, signal);
        assertNull(signal.cause());
        var actual = new User(42);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(42, waiting.until(condition("ready", ignored -> satisfied())).id());
        AwaitResult<User, User> result = diagnostic(waiting).until(condition("ready", ignored -> satisfied()));
        switch (result) {
            case AwaitResult.Satisfied(var history, var user) -> assertSame(actual, user);
            case AwaitResult.Failed(var history, var failure) -> org.junit.jupiter.api.Assertions.fail(failure.message());
        }
        assertSame(actual, await(() -> java.util.Optional.of(actual)).usingTime(time, time)
                .until(hasValue(condition("present", ignored -> satisfied()))));
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(singleElement(condition("single", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(firstElement(condition("first", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.Map.of("user", actual)).usingTime(time, time)
                .until(valueFor("user", condition("mapped", ignored -> satisfied()))).id());
        User absent = null;
        assertNull(await(() -> absent).usingTime(time, time).until(condition("null is valid", ignored -> satisfied())));
    }

    @Test
    void conditionsCanFailWithoutDuplicatingTheirDescription() {
        AwaitResult<String, String> result = tryAwait(() -> "pending")
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                .until(condition("status is ready", value -> unsatisfied()));
        var failure = failure(AwaitFailure.Reason.TIMEOUT, result);
        assertThat(failure.message()).contains("Condition: status is ready", "Actual: \"pending\"").doesNotContain("Mismatch:");
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
