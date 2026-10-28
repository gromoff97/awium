package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.internal.ConditionEvaluation;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

final class ConditionTestRuntime {

    static Condition.Typed<Object, Object> diagnosticCondition(String description, Condition.Reference<?> reference) {
        return new Condition.Typed<>(description, reference, () -> ConditionEvaluation::satisfied);
    }

    static <SOURCE, RESULT> ConditionEvaluation<? extends RESULT> evaluate(
            Condition.Typed<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return evaluated(ConditionSupport.bind(condition).probe(actual));
    }

    static <SOURCE> ConditionEvaluation<? extends SOURCE> evaluate(
            PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static <SOURCE> ConditionEvaluation<? extends SOURCE> evaluate(
            Condition.ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static <RESULT> ConditionEvaluation<? extends RESULT> evaluated(ConditionEvaluation<? extends RESULT> attempt) {
        if (attempt instanceof ConditionEvaluation.Uncontrolled<?> failure) {
            throw new AssertionError("condition failed", failure.status().cause());
        }
        return attempt;
    }

    static void assertUnsatisfied(ConditionEvaluation<?> evaluation) {
        assertInstanceOf(ConditionEvaluation.Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(ConditionEvaluation<? extends RESULT> evaluation) {
        return evaluation instanceof ConditionEvaluation.Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    static String mismatch(ConditionEvaluation<?> evaluation) {
        return assertInstanceOf(ConditionEvaluation.Unsatisfied.class, evaluation).status().mismatch();
    }

    @SuppressWarnings("unchecked")
    static <VALUE, FAILURE extends Throwable> VALUE throwUnchecked(Throwable failure) throws FAILURE {
        throw (FAILURE) failure;
    }

    private ConditionTestRuntime() {}
}
