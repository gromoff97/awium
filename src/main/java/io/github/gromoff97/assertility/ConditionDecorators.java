package io.github.gromoff97.assertility;

import java.util.Locale;
import java.util.Objects;

final class ConditionDecorators {

    private ConditionDecorators() {
    }

    static <S, R> ExplainedCondition<S, R> explain(
            Condition<S, R> condition, String explanation) {
        return new ExplainedCondition<>(condition, literal(explanation));
    }

    static <S, R> ExplainedCondition<S, R> explain(
            Condition<S, R> condition, String format, Object[] arguments) {
        return new ExplainedCondition<>(condition, formatted(format, arguments));
    }

    static <S> ExplainedPreservingCondition<S> explain(
            PreservingCondition<S> condition, String explanation) {
        return new ExplainedPreservingCondition<>(condition, literal(explanation));
    }

    static <S> ExplainedPreservingCondition<S> explain(
            PreservingCondition<S> condition, String format, Object[] arguments) {
        return new ExplainedPreservingCondition<>(condition, formatted(format, arguments));
    }

    static ExplainedPresent explain(Present condition, String explanation) {
        return new ExplainedPresent(condition, literal(explanation));
    }

    static ExplainedPresent explain(Present condition, String format, Object[] arguments) {
        return new ExplainedPresent(condition, formatted(format, arguments));
    }

    static ExplainedStructuralCondition explain(
            StructuralCondition condition, String explanation) {
        return new ExplainedStructuralCondition(condition, literal(explanation));
    }

    static ExplainedStructuralCondition explain(
            StructuralCondition condition, String format, Object[] arguments) {
        return new ExplainedStructuralCondition(condition, formatted(format, arguments));
    }

    static String literal(String explanation) {
        return Validation.nonBlank(explanation, "explanation");
    }

    static String formatted(String format, Object[] arguments) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        return Validation.nonBlank(
                String.format(Locale.ROOT, format, arguments), "explanation");
    }
}
