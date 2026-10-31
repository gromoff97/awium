package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.uncontrolled;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@SuppressWarnings("removal")
class ConditionThrowableTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("ordinaryConditionFailures")
    void ordinaryAwaitRetainsConditionThrowables(Throwable cause, Condition<String, String> check) {

        var waiting = await(() -> "actual").every(ofNanos(1));

        assertSame(cause, assertThrows(AwaitExecutionException.class, () -> waiting.until(check)).getCause());
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("composedConditionFailures")
    void conditionThrowablesStayUncontrolledThroughTerminalsAndComposition(
            Throwable cause, Condition<String, String> check,
            Function<Condition<String, String>, AwaitResult<?, ?>> wait) {
        Failed<?, ?> result = assertInstanceOf(Failed.class, wait.apply(check));

        assertEquals(CONDITION_FAILED, result.failure().reason());
        assertSame(cause, result.failure().cause());
        var attempt = uncontrolled(CONDITION, attempts(result).getLast());
        assertSame(cause, attempt.cause());
        assertFalse(currentThread().isInterrupted());
    }

    private static Stream<Arguments> ordinaryConditionFailures() {
        return throwables().flatMap(cause -> throwingConditions(cause)
                .map(check -> arguments(named(cause.getClass().getSimpleName(), cause), check)));
    }

    private static Stream<Arguments> composedConditionFailures() {
        var waits = List.<Named<Function<Condition<String, String>, AwaitResult<?, ?>>>>of(
                named("value", check -> tryAwait(() -> "actual").every(ofNanos(1)).until(check)),
                named("optional", check -> tryAwait(() -> Optional.of("actual")).until(hasValue(check))),
                named("collection", check -> tryAwait(() -> List.of("actual")).until(singleElement(check))),
                named("captured", check -> tryAwait(() -> "actual").every(ofNanos(1))
                        .until(captured(isNotNull, check))));
        return throwables().flatMap(cause -> throwingConditions(cause).flatMap(check -> waits.stream()
                .map(wait -> arguments(named(cause.getClass().getSimpleName(), cause), check, wait))));
    }

    private static Stream<Named<Condition<String, String>>> throwingConditions(Throwable cause) {
        return Stream.of(named("callback", condition("callback", actual -> { throw cause; })),
                named("factory", conditionPerWait("factory", () -> rethrow(cause))));
    }

    private static Stream<Throwable> throwables() {
        return Stream.of(new Throwable("direct"), new Exception("checked"), new InterruptedException("interrupted"),
                new RuntimeException("runtime"),
                new AssertionError("assertion"), new LinkageError("linkage"),
                new InternalError("fatal"), new ThreadDeath());
    }
}
