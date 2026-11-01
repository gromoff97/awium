package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.conditions.function.CheckedConsumer;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.conditions.common.SizeRelation.AT_LEAST;
import static io.github.gromoff97.awium.conditions.common.SizeRelation.AT_MOST;
import static io.github.gromoff97.awium.conditions.common.SizeRelation.GREATER;
import static io.github.gromoff97.awium.conditions.common.SizeRelation.LESS;

public final class Conditions {

    public static final PreservingCondition<Object> isNull = new NullCondition(true);
    public static final PreservingCondition<Object> isNotNull = new NullCondition(false);

    private Conditions() {}

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <CHECK extends AwaitCondition> CapturedCondition<CHECK> captured(CHECK... conditions) {
        // Keep the check type so mixed input bounds can be inferred from the terminal call.
        return new CapturedCondition<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT, EXPECTED> CapturedCondition<ApplicableCondition<INPUT, Reference<? super EXPECTED>>> captured(
            ApplicableCondition<? super INPUT, ? super Reference<? super EXPECTED>>... conditions) {
        return new CapturedCondition<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT> CapturedCondition<InputCondition<INPUT>> captured(
            InputCondition<? super INPUT>... conditions) {
        return new CapturedCondition<>(conditions);
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> condition(String description,
            AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        return new DefinedCondition<>(description, null, evaluation);
    }

    public static <INPUT, RESULT> Condition<INPUT, RESULT> conditionPerWait(String description,
            Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
        return new DefinedCondition<>(description, null, evaluatorFactory);
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

    public static <RESULT> Condition<Object, RESULT> instanceOf(Class<RESULT> type) {
        return new InstanceOfCondition<>(type, false);
    }

    public static <RESULT> Condition<Object, RESULT> exactlyInstanceOf(Class<RESULT> type) {
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
        return new BoundCondition<>("greater than", bound, GREATER);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atLeast(BOUND bound) {
        return new BoundCondition<>("at least", bound, AT_LEAST);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> lessThan(BOUND bound) {
        return new BoundCondition<>("less than", bound, LESS);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atMost(BOUND bound) {
        return new BoundCondition<>("at most", bound, AT_MOST);
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
