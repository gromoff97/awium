package io.github.gromoff97.awium;

import java.util.Locale;
import java.util.Objects;

final class ConditionDecorators {

    private ConditionDecorators() {
    }

    static <S, R> Condition.Explained<S, R> explain(
            Condition<S, R> condition, String explanation) {
        return new Condition.Explained<>(condition, literal(explanation));
    }

    static <S, R> Condition.Explained<S, R> explain(
            Condition<S, R> condition, String format, Object[] arguments) {
        return new Condition.Explained<>(condition, formatted(format, arguments));
    }

    static <S> PreservingCondition.Explained<S> explain(
            PreservingCondition<S> condition, String explanation) {
        return new PreservingCondition.Explained<>(condition, literal(explanation));
    }

    static <S> PreservingCondition.Explained<S> explain(
            PreservingCondition<S> condition, String format, Object[] arguments) {
        return new PreservingCondition.Explained<>(condition,
                formatted(format, arguments));
    }

    static Present.Explained explain(Present condition, String explanation) {
        return new Present.Explained(condition, literal(explanation));
    }

    static Present.Explained explain(Present condition, String format,
            Object[] arguments) {
        return new Present.Explained(condition, formatted(format, arguments));
    }

    static StructuralCondition.Explained explain(
            StructuralCondition condition, String explanation) {
        return new StructuralCondition.Explained(condition,
                literal(explanation));
    }

    static StructuralCondition.Explained explain(
            StructuralCondition condition, String format, Object[] arguments) {
        return new StructuralCondition.Explained(condition,
                formatted(format, arguments));
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
