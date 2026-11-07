package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.conditions.string.TextMatchCondition.Match;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import java.util.regex.Pattern;

public final class StringConditions {

    public static final PreservingCondition<CharSequence> empty = new EmptyCondition(true);
    public static final PreservingCondition<CharSequence> nonEmpty = new EmptyCondition(false);
    public static final PreservingCondition<CharSequence> blank = new BlankCondition(true);
    public static final PreservingCondition<CharSequence> nonBlank = new BlankCondition(false);

    private StringConditions() {}

    public static PreservingCondition<CharSequence> contains(CharSequence expected) {
        return new TextMatchCondition(expected, Match.CONTAINS);
    }

    public static PreservingCondition<CharSequence> containsAll(Iterable<? extends CharSequence> expected) {
        return new TextSetCondition(expected, false);
    }

    public static PreservingCondition<CharSequence> doesNotContain(CharSequence unexpected) {
        return new TextMatchCondition(unexpected, Match.DOES_NOT_CONTAIN);
    }

    public static PreservingCondition<CharSequence> doesNotContainAny(Iterable<? extends CharSequence> unexpected) {
        return new TextSetCondition(unexpected, true);
    }

    public static PreservingCondition<CharSequence> containsIgnoringCase(CharSequence expected) {
        return new ContainsIgnoringCaseCondition(expected);
    }

    public static PreservingCondition<CharSequence> startsWith(CharSequence prefix) {
        return new TextMatchCondition(prefix, Match.STARTS_WITH);
    }

    public static PreservingCondition<CharSequence> doesNotStartWith(CharSequence prefix) {
        return new TextMatchCondition(prefix, Match.DOES_NOT_START_WITH);
    }

    public static PreservingCondition<CharSequence> endsWith(CharSequence suffix) {
        return new TextMatchCondition(suffix, Match.ENDS_WITH);
    }

    public static PreservingCondition<CharSequence> doesNotEndWith(CharSequence suffix) {
        return new TextMatchCondition(suffix, Match.DOES_NOT_END_WITH);
    }

    public static PreservingCondition<CharSequence> matchesRegex(CharSequence regex) {
        return new RegexCondition(regex, false);
    }

    public static PreservingCondition<CharSequence> matchesRegex(Pattern expected) {
        return new RegexCondition(expected, false);
    }

    public static PreservingCondition<CharSequence> doesNotMatchRegex(CharSequence regex) {
        return new RegexCondition(regex, true);
    }

    public static PreservingCondition<CharSequence> doesNotMatchRegex(Pattern unexpected) {
        return new RegexCondition(unexpected, true);
    }

    public static PreservingCondition<CharSequence> contentEquals(CharSequence expected) {
        return new TextMatchCondition(expected, Match.CONTENT_EQUALS);
    }

    public static PreservingCondition<CharSequence> equalToIgnoringCase(CharSequence expected) {
        return new TextMatchCondition(expected, Match.EQUALS_IGNORING_CASE);
    }

    public static PreservingCondition<CharSequence> notEqualToIgnoringCase(CharSequence unexpected) {
        return new TextMatchCondition(unexpected, Match.NOT_EQUALS_IGNORING_CASE);
    }

    public static PreservingCondition<CharSequence> hasLength(int expected) {
        return new HasLengthCondition(expected, CommonConditions::equalTo);
    }

    public static PreservingCondition<CharSequence> hasLengthNotEqualTo(int unexpected) {
        return new HasLengthCondition(unexpected, CommonConditions::notEqualTo);
    }

    public static PreservingCondition<CharSequence> hasLengthGreaterThan(int lowerBound) {
        return new HasLengthCondition(lowerBound, CommonConditions::greaterThan);
    }

    public static PreservingCondition<CharSequence> hasLengthAtLeast(int lowerBound) {
        return new HasLengthCondition(lowerBound, CommonConditions::atLeast);
    }

    public static PreservingCondition<CharSequence> hasLengthLessThan(int upperBound) {
        return new HasLengthCondition(upperBound, CommonConditions::lessThan);
    }

    public static PreservingCondition<CharSequence> hasLengthAtMost(int upperBound) {
        return new HasLengthCondition(upperBound, CommonConditions::atMost);
    }

    public static PreservingCondition<CharSequence> hasLengthBetween(int lowerBound, int upperBound) {
        return new HasLengthCondition(lowerBound, upperBound);
    }
}
