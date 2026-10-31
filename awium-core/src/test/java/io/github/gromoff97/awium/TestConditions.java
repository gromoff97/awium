package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

final class TestConditions {
    private TestConditions() {}

    static final PreservingCondition<Object> accepted = new PreservingCondition<>(
            "accepted", null, actual -> satisfied(actual)) {};

    static <INPUT, RESULT> Condition<INPUT, RESULT> check(
            String description, AwaitAttempt<? super INPUT, ? extends RESULT> attempt) {
        return new DefinedCondition<>(description, null, attempt);
    }

    static <INPUT, RESULT> Condition<INPUT, RESULT> checkPerWait(
            String description, Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
        return new DefinedCondition<>(description, null, factory);
    }

    static <INPUT> PreservingCondition<INPUT> matching(CheckedPredicate<? super INPUT> predicate) {
        return new PreservingCondition<>("test predicate", null,
                (INPUT actual) -> predicate.test(actual) ? satisfied(actual) : unsatisfied()) {};
    }

    static <EXPECTED> ComparisonCondition<EXPECTED> expected(EXPECTED expected) {
        return new ComparisonCondition<>("expected value", new Reference<>("Expected", expected),
                actual -> deepEquals(actual, expected) ? satisfied(actual) : unsatisfied("unexpected value")) {};
    }
}
