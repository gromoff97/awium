package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.function.Predicate;
import java.util.concurrent.Callable;
import java.util.List;

import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class TestConditions {
    private TestConditions() {}

    public static <INPUT> Condition<INPUT, INPUT> accepted() {
        return check("accepted", actual -> satisfied(actual));
    }

    public static Condition<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new TestCondition<>(description, reference, AttemptResult::satisfied);
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> check(
            String description, AwaitAttempt<? super INPUT, ? extends RESULT> attempt) {
        return new TestCondition<>(description, null, attempt);
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> checkPerWait(
            String description, Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
        return new TestCondition<>(description, null, factory);
    }

    public static <INPUT> Condition<INPUT, INPUT> matching(Predicate<? super INPUT> predicate) {
        return check("test predicate", actual -> predicate.test(actual) ? satisfied(actual) : unsatisfied("predicate returned false"));
    }

    public static <INPUT> Condition<INPUT, INPUT> expected(Object expected) {
        return new TestCondition<>("expected value", new Reference<>("Expected", expected),
                actual -> deepEquals(actual, expected) ? satisfied(actual) : unsatisfied("unexpected value"));
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> selecting(AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        return check("test selection", evaluation);
    }
}
