package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import java.util.function.Predicate;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class TestConditions {
    private TestConditions() {}

    public static final PreservingCondition<Object> accepted = new PreservingCondition<>(
            "accepted", null, actual -> satisfied(actual)) {};

    public static <INPUT, RESULT> Condition<INPUT, RESULT> check(
            String description, AwaitAttempt<? super INPUT, ? extends RESULT> attempt) {
        return new DefinedCondition<>(description, null, attempt);
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> checkPerWait(
            String description, Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
        return new DefinedCondition<>(description, null, factory);
    }

    public static <INPUT> PreservingCondition<INPUT> matching(Predicate<? super INPUT> predicate) {
        return new PreservingCondition<>("test predicate", null,
                (INPUT actual) -> predicate.test(actual) ? satisfied(actual) : unsatisfied()) {};
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> expected(EXPECTED expected) {
        return new ComparisonCondition<>("expected value", new Reference<>("Expected", expected),
                actual -> deepEquals(actual, expected) ? satisfied(actual) : unsatisfied("unexpected value")) {};
    }
}
