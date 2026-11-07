package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.dsl.AwaitCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.dsl.TypedCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.function.checked.CheckedConsumer;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.conditions.common.BoundCondition.Relation.AT_LEAST;
import static io.github.gromoff97.awium.conditions.common.BoundCondition.Relation.AT_MOST;
import static io.github.gromoff97.awium.conditions.common.BoundCondition.Relation.GREATER_THAN;
import static io.github.gromoff97.awium.conditions.common.BoundCondition.Relation.LESS_THAN;
import static io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.dsl.AwaitCondition.InputCondition;

public final class CommonConditions {

    public static final PreservingCondition<Object> isNull = new NullCondition(true);
    public static final PreservingCondition<Object> isNotNull = new NullCondition(false);

    private CommonConditions() {}

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <CHECK extends AwaitCondition> CollectingCondition<CHECK> captured(CHECK... conditions) {
        // Keep the check type so mixed input bounds can be inferred from the terminal call.
        return new CapturedCondition<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT, EXPECTED> CollectingCondition<ApplicableCondition<INPUT, Reference<? super EXPECTED>>> captured(
            ApplicableCondition<? super INPUT, ? super Reference<? super EXPECTED>>... conditions) {
        return new CapturedCondition<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT> CollectingCondition<InputCondition<INPUT>> captured(
            InputCondition<? super INPUT>... conditions) {
        return new CapturedCondition<>(conditions);
    }

    public static <INPUT, RESULT> TypedCondition<INPUT, RESULT> condition(String description,
            AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        return new CallbackCondition<>(description, evaluation);
    }

    public static <INPUT, RESULT> TypedCondition<INPUT, RESULT> conditionPerWait(String description,
            Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
        return new CallbackCondition<>(description, evaluatorFactory);
    }

    public static <INPUT> PreservingCondition<INPUT> asserted(CheckedConsumer<? super INPUT> assertion) {
        return new AssertedCondition<>(assertion);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> equalTo(EXPECTED expected) {
        return new EqualityCondition<>(expected, false, false);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notEqualTo(EXPECTED unexpected) {
        return new EqualityCondition<>(unexpected, false, true);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> sameAs(EXPECTED expected) {
        return new EqualityCondition<>(expected, true, false);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notSameAs(EXPECTED unexpected) {
        return new EqualityCondition<>(unexpected, true, true);
    }

    public static <RESULT> TypedCondition<Object, RESULT> instanceOf(Class<RESULT> type) {
        return new InstanceOfCondition<>(type, false);
    }

    public static <RESULT> TypedCondition<Object, RESULT> exactlyInstanceOf(Class<RESULT> type) {
        return new InstanceOfCondition<>(type, true);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> in(Iterable<? extends EXPECTED> expected) {
        return new InCondition<>(expected, false);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notIn(Iterable<? extends EXPECTED> unexpected) {
        return new InCondition<>(unexpected, true);
    }

    public static <INPUT> PreservingCondition<INPUT> matches(CheckedPredicate<? super INPUT> predicate) {
        return new MatchesCondition<>(predicate);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> greaterThan(BOUND bound) {
        return new BoundCondition<>(GREATER_THAN, bound);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atLeast(BOUND bound) {
        return new BoundCondition<>(AT_LEAST, bound);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> lessThan(BOUND bound) {
        return new BoundCondition<>(LESS_THAN, bound);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atMost(BOUND bound) {
        return new BoundCondition<>(AT_MOST, bound);
    }

    public static <BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> between(BOUND lowerBound, BOUND upperBound) {
        return new RangeCondition<>(lowerBound, upperBound, true);
    }

    public static <BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> strictlyBetween(BOUND lowerBound, BOUND upperBound) {
        return new RangeCondition<>(lowerBound, upperBound, false);
    }
}
