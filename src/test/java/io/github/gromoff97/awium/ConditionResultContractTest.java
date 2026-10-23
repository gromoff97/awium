package io.github.gromoff97.awium;

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
        AwaitResult<String, String> result = Await.await(() -> "pending")
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
        ConditionTestRuntime.assertUnsatisfied(plain);
        assertEquals(new Unsatisfied<>(null, null), plain);
        assertEquals(plain, unsatisfied(null));
        assertEquals(plain, unsatisfied(null, null));
        var cause = new IllegalStateException("details");
        assertSame(cause, unsatisfied(null, cause).cause());
        assertEquals(new Unsatisfied<>("failed", null), unsatisfied("failed", null));
    }

}
