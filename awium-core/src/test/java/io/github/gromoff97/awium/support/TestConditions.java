package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.function.Predicate;
import java.util.concurrent.Callable;
import java.util.List;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class TestConditions {
    private TestConditions() {}

    public static final PreservingCondition<Object> accepted = new PreservingCondition<>() {
        @Override public String description() { return "accepted"; }
        @Override public AttemptResult<?> probe(Object actual) { return satisfied(actual); }
    };

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

    public static <INPUT> PreservingCondition<INPUT> matching(Predicate<? super INPUT> predicate) {
        return new PreservingCondition<>() {
            @Override public String description() { return "test predicate"; }
            @Override public AttemptResult<?> probe(INPUT actual) { return predicate.test(actual) ? satisfied(actual) : unsatisfied("predicate returned false"); }
        };
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> expected(EXPECTED expected) {
        return new ComparisonCondition<>() {
            @Override public String description() { return "expected value"; }
            @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", expected)); }
            @Override public AttemptResult<?> probe(Object actual) { return deepEquals(actual, expected) ? satisfied(actual) : unsatisfied("unexpected value"); }
        };
    }

    public static <INPUT> SelectedCondition<INPUT> selecting(AwaitAttempt<? super INPUT, ?> evaluation) {
        return new SelectedCondition<>() {
            @Override public String description() { return "test selection"; }
            @Override public AttemptResult<?> probe(INPUT actual) throws Throwable { return evaluation.probe(actual); }
        };
    }

    public static <INPUT> MapValueCondition<INPUT> selectingValue(AwaitAttempt<? super INPUT, ?> evaluation) {
        return new MapValueCondition<>() {
            @Override public String description() { return "test map value"; }
            @Override public AttemptResult<?> probe(INPUT actual) throws Throwable { return evaluation.probe(actual); }
        };
    }
}
