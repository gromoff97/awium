package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.await;
import static io.github.gromoff97.awium.OptionalConditions.await;
import static io.github.gromoff97.awium.MapConditions.await;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionResult.*;
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
    void satisfactionWithoutAPayloadPreservesConcreteAndSelectedObservations() {
        ConditionResult<String> signal = satisfied();
        assertNull(assertInstanceOf(Satisfied.class, signal).result());
        assertNull(signal.cause());
        var actual = new User(42);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(42, waiting.until(Conditions.condition("ready", ignored -> satisfied()).because("original user")).id());
        AwaitResult<User, User> result = waiting.tryUntil(Conditions.condition("ready", ignored -> satisfied()));
        switch (result) {
            case AwaitResult.Satisfied(var attempts, var user) -> assertSame(actual, user);
            case AwaitResult.Failed(var attempts, var failure) -> org.junit.jupiter.api.Assertions.fail(failure.message());
        }
        assertSame(actual, await(() -> java.util.Optional.of(actual)).usingTime(time, time)
                .until(OptionalConditions.hasValue(Conditions.condition("present", ignored -> satisfied())).because("optional")));
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(CollectionConditions.single(Conditions.condition("single", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(CollectionConditions.first(Conditions.condition("first", ignored -> satisfied()))).id());
        assertEquals(42, await(() -> java.util.Map.of("user", actual)).usingTime(time, time)
                .until(MapConditions.valueFor("user", Conditions.condition("mapped", ignored -> satisfied()))).id());
        User absent = null;
        assertNull(await(() -> absent).usingTime(time, time).until(Conditions.condition("null is valid", ignored -> satisfied())));
    }

    @Test
    void mappingSatisfiedResultChangesItsValue() {
        ConditionResult<String> mapped = satisfied(42)
                .mapSatisfied(Object::toString);

        var result = assertInstanceOf(ConditionResult.Satisfied.class, mapped);
        assertEquals("42", result.result());
        assertNull(mapped.cause());
    }

    @Test
    void mappingUnsatisfiedSkipsTheCallbackAndKeepsItsCause() {
        var cause = new IllegalStateException("cause");
        ConditionResult<String> mapped = unsatisfied("mismatch", cause).mapSatisfied(value -> {
            throw new AssertionError("mapping must not run");
        });
        var failure = assertInstanceOf(ConditionResult.Unsatisfied.class, mapped);
        assertEquals("mismatch", failure.mismatch());
        assertSame(cause, failure.cause());
    }

    @Test
    void conditionsCanFailWithoutDuplicatingTheirDescription() {
        var time = new FakeTime(0);
        AwaitResult<String, String> result = await(() -> "pending")
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                .tryUntil(Conditions.condition("status is ready", value -> unsatisfied())
                        .because("delivery must complete"));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
        assertTrue(failure.message().contains("Condition: status is ready"));
        assertTrue(failure.message().contains("Because: delivery must complete"));
        assertTrue(failure.message().contains("Actual: pending"));
        assertFalse(failure.message().contains("Mismatch:"));
    }

    @Test
    void unsatisfiedAcceptsOptionalDiagnostics() {
        var plain = unsatisfied();
        assertEquals(new Unsatisfied<>(null, null), plain);
        assertEquals(plain, unsatisfied(null));
        assertEquals(plain, unsatisfied(null, null));
        var cause = new IllegalStateException("details");
        assertSame(cause, unsatisfied(null, cause).cause());
        assertEquals(new Unsatisfied<>("failed", null), unsatisfied("failed", null));
    }

}
