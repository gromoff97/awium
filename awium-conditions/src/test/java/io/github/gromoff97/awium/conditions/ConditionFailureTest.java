package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.thrown;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class ConditionFailureTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptionAfterEvaluationRetainsTheOriginalCauseAndNestedLocation(boolean throwing) {
        var cause = new IllegalStateException("not ready");
        var check = hasValue(condition("ready", (String actual) -> {
            currentThread().interrupt();
            if (throwing) throw cause;
            return unsatisfied("not ready", cause);
        }));
        var failure = assertThrows(ProbeFailure.class, () -> bind(check).probe(Optional.of("actual")));
        assertSame(cause, failure.getCause());
        assertEquals("optional value", failure.location());
        assertTrue(currentThread().isInterrupted());
    }





    @ParameterizedTest
    @MethodSource("nullEvaluationSources")
    void nullEvaluationIsRejectedByEveryComposition(Function<Condition<String, String>, Executable> probe) {
        var failure = assertThrows(Throwable.class, probe.apply(condition("broken", actual -> null)));
        if (failure instanceof ProbeFailure nested) failure = nested.getCause();
        assertThat(failure).isInstanceOf(NullPointerException.class).hasMessage("condition returned null ConditionResult");
        assertFalse(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("nullEvaluationSources")
    void nullEvaluationDoesNotClearAnInterrupt(Function<Condition<String, String>, Executable> probe) {
        var failure = assertThrows(Throwable.class, probe.apply(condition("broken", actual -> {
            currentThread().interrupt();
            return null;
        })));
        if (failure instanceof ProbeFailure nested) failure = nested.getCause();
        assertThat(failure).isInstanceOf(NullPointerException.class).hasMessage("condition returned null ConditionResult");
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @MethodSource("interruptedEvaluations")
    void capturedInterruptDoesNotCompleteTheStage(ConditionResult<String> evaluation, Class<? extends Throwable> causeType, String message) {
        var check = captured(condition("first stage", (String actual) -> {
            currentThread().interrupt();
            return evaluation;
        }), condition("second stage", (String actual) -> satisfied(actual)));
        var run = ConditionExecution.<String, List<String>>start(check, false);
        run.beginAttempt();
        assertThat(assertThrows(Throwable.class, () -> run.probe("actual"))).isExactlyInstanceOf(causeType).hasMessage(message);
        assertTrue(run.result().isEmpty());
        assertFalse(run.complete());
        assertTrue(currentThread().isInterrupted());
    }

    private static List<Named<Function<Condition<String, String>, Executable>>> nullEvaluationSources() {
        return List.of(named("value", check -> () -> bind(check).probe("actual")),
                named("captured", check -> () -> bind(captured(check, check)).probe("actual")),
                named("optional", check -> () -> bind(hasValue(check)).probe(Optional.of("actual"))),
                named("collection", check -> () -> bind(singleElement(check)).probe(List.of("actual"))),
                named("map", check -> () -> bind(valueFor("key", check)).probe(Map.of("key", "actual"))));
    }

    private static List<Arguments> interruptedEvaluations() {
        return List.of(argumentSet("null result", null, NullPointerException.class, "condition returned null ConditionResult"),
                argumentSet("satisfied result", satisfied("actual"), InterruptedException.class, "caller thread interrupt flag was set"));
    }
}
