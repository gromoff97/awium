package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConditionDecorationTest {

    @Test
    void openConditionKeepsItsDelegateAndFormatsExplanationEagerly() throws Exception {
        var actual = new Object();
        var condition = new Condition<Object, Object>() {
            @Override
            public Evaluation<Object> evaluate(Object value) {
                return Evaluation.satisfied(value);
            }
        };

        var literal = condition.because("the value must be ready");
        var formatted = condition.because("attempt %d", 3);

        assertSame(condition, literal.delegate());
        assertEquals("the value must be ready", literal.explanation());
        assertSame(condition, formatted.delegate());
        assertEquals("attempt 3", formatted.explanation());
        assertSame(actual, condition.evaluate(actual).result());
        assertEquals("custom condition", condition.description());
    }

    @Test
    void closedDescriptorsKeepTheirRuntimeAndCloseDecoration() throws Exception {
        ConditionRuntime<Object, Object> preservingRuntime = runtime();
        ConditionRuntime<Optional<?>, Object> presentRuntime = new ConditionRuntime<>(
                value -> Evaluation.satisfied(value.orElse(null)), () -> "present", null);
        ConditionRuntime<Object, Object> structuralRuntime = runtime();
        var preserving = new PreservingCondition<>(preservingRuntime);
        var present = new Present(presentRuntime);
        var structural = new StructuralCondition(structuralRuntime);

        var explainedPreserving = preserving.because("preserving");
        var explainedPresent = present.because("present %s", "value");
        var explainedStructural = structural.because("structural");

        assertSame(preservingRuntime, preserving.runtime());
        assertSame(preserving, explainedPreserving.delegate());
        assertEquals("preserving", explainedPreserving.explanation());
        assertSame(presentRuntime, present.runtime());
        assertSame(present, explainedPresent.delegate());
        assertEquals("present value", explainedPresent.explanation());
        assertEquals("present literal", present.because("present literal").explanation());
        assertSame(structuralRuntime, structural.runtime());
        assertSame(structural, explainedStructural.delegate());
        assertEquals("structural", explainedStructural.explanation());
        assertEquals("structural value",
                structural.because("structural %s", "value").explanation());
        assertSame(preservingRuntime.evaluator(),
                preservingRuntime.explained("why").evaluator());
        assertEquals("why", preservingRuntime.explained("why").explanation());
        assertSame(this, preservingRuntime.evaluate(this).result());
    }

    @Test
    void explanationValidationHappensBeforeEvaluation() {
        var evaluations = new int[1];
        var condition = new Condition<Object, Object>() {
            @Override
            public Evaluation<Object> evaluate(Object actual) {
                evaluations[0]++;
                return Evaluation.satisfied(actual);
            }
        };

        assertEquals("explanation must not be null",
                assertThrows(NullPointerException.class,
                        () -> condition.because((String) null)).getMessage());
        assertEquals("explanation must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> condition.because(" \n ")).getMessage());
        assertEquals("format must not be null",
                assertThrows(NullPointerException.class,
                        () -> condition.because(null, 1)).getMessage());
        assertEquals("arguments must not be null",
                assertThrows(NullPointerException.class,
                        () -> condition.because("%s", (Object[]) null)).getMessage());
        assertEquals("explanation must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> condition.because("%s", " ")).getMessage());
        assertThrows(IllegalFormatException.class, () -> condition.because("%q", 1));
        assertThrows(RenderFailure.class,
                () -> condition.because("%s", new ThrowingRenderer()));
        assertEquals(0, evaluations[0]);
    }

    @Test
    void formattedExplanationsUseRootLocale() {
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
        try {
            assertEquals("1.5", new PreservingCondition<>(runtime())
                    .because("%.1f", 1.5).explanation());
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    private static ConditionRuntime<Object, Object> runtime() {
        return new ConditionRuntime<>(Evaluation::satisfied, () -> "condition", null);
    }

    private static final class ThrowingRenderer {
        @Override
        public String toString() {
            throw new RenderFailure();
        }
    }

    private static final class RenderFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
