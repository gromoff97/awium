package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.ConditionAssessment;
import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.results.AwaitAttempt;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionAssessmentTest {

    private static final AwaitAttempt.Context.Sequence SEQUENCE =
            new AwaitAttempt.Context.Sequence(1, 2, 1, "second stage", "business reason", null);

    @Test
    void flatMapUsesNestedContextAfterSatisfiedEvaluation() {
        var nested = new ConditionAssessment<>(satisfied("ready"), SEQUENCE);

        ConditionAssessment<String> result = ConditionAssessment.plain(satisfied(42)).flatMap(value -> nested);

        assertSame(SEQUENCE, result.context());
        assertEquals("ready", assertInstanceOf(ConditionEvaluation.Satisfied.class, result.evaluation()).result());
    }

    @Test
    void flatMapSkipsContinuationAndPreservesCurrentContextAfterMismatch() {
        var calls = new int[1];
        var assessment = new ConditionAssessment<Integer>(unsatisfied("not ready"), SEQUENCE);

        ConditionAssessment<String> result = assessment.flatMap(value -> {
            calls[0]++;
            return ConditionAssessment.plain(satisfied(value.toString()));
        });

        assertEquals(0, calls[0]);
        assertSame(SEQUENCE, result.context());
        assertEquals("not ready", assertInstanceOf(ConditionEvaluation.Unsatisfied.class, result.evaluation()).mismatch());
    }

    @Test
    void mapEvaluationAndWithContextChangeOnlyTheirOwnedPart() {
        var plain = ConditionAssessment.plain(satisfied(42));

        ConditionAssessment<String> mapped = plain.mapEvaluation(value -> satisfied(value.toString()));
        ConditionAssessment<String> contextualized = mapped.withContext(SEQUENCE);

        assertSame(AwaitAttempt.Context.Plain.INSTANCE, mapped.context());
        assertEquals("42", assertInstanceOf(ConditionEvaluation.Satisfied.class, mapped.evaluation()).result());
        assertSame(SEQUENCE, contextualized.context());
        assertSame(mapped.evaluation(), contextualized.evaluation());
    }
}
