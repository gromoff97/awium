package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        var preserving = new PreservingCondition<>(preservingRuntime);
        var present = new Present(presentRuntime);
        var structural = AwaitConditions.nonEmpty;

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

    @Test
    void openAdaptersKeepTheDelegateSemanticsAndOptionalExplanation()
            throws Exception {
        var evaluations = new int[1];
        var condition = new Condition<Object, String>() {
            @Override
            public Evaluation<String> evaluate(Object actual) {
                evaluations[0]++;
                return Evaluation.satisfied("selected");
            }

            @Override
            public String description() {
                return "named condition";
            }
        };

        ConditionRuntime<Object, String> raw = ConditionRuntime.open(condition);
        ConditionRuntime<Object, String> explained = ConditionRuntime.open(
                condition.because("because value"));

        assertEquals("selected", raw.evaluate(this).result());
        assertEquals("selected", explained.evaluate(this).result());
        assertEquals(2, evaluations[0]);
        assertEquals("named condition", raw.description().get());
        assertNull(raw.explanation());
        assertEquals("because value", explained.explanation());

        var nullEvaluation = new Condition<Object, Object>() {
            @Override
            public Evaluation<Object> evaluate(Object actual) {
                return null;
            }
        };
        assertNull(ConditionRuntime.open(nullEvaluation).evaluate(this));
    }

    @Test
    void closedAdaptersRecoverTheirExactResultContractsAndExplanations()
            throws Exception {
        var description = (ConditionRuntime.Description) () -> "descriptor";
        var actual = new StringBuilder("actual");
        var preserving = new PreservingCondition<>(new ConditionRuntime<>(
                value -> Evaluation.satisfied(new Object()), description, null));

        ConditionRuntime<StringBuilder, StringBuilder> rawPreserving =
                ConditionRuntime.preserving(preserving);
        ConditionRuntime<StringBuilder, StringBuilder> explainedPreserving =
                ConditionRuntime.preserving(preserving.because("preserving"));

        assertSame(actual, rawPreserving.evaluate(actual).result());
        assertSame(actual, explainedPreserving.evaluate(actual).result());
        assertSame(description, rawPreserving.description());
        assertNull(rawPreserving.explanation());
        assertEquals("preserving", explainedPreserving.explanation());

        var present = new Present(new ConditionRuntime<>(
                value -> Evaluation.satisfied(value.orElse(null)), description, null));
        ConditionRuntime<Optional<String>, String> rawPresent =
                ConditionRuntime.present(present);
        ConditionRuntime<Optional<String>, String> explainedPresent =
                ConditionRuntime.present(present.because("present"));

        assertEquals("value", rawPresent.evaluate(Optional.of("value")).result());
        assertEquals("value",
                explainedPresent.evaluate(Optional.of("value")).result());
        assertSame(description, rawPresent.description());
        assertNull(rawPresent.explanation());
        assertEquals("present", explainedPresent.explanation());

        var structural = AwaitConditions.nonEmpty;
        var actualCollection = new java.util.ArrayList<>(java.util.List.of("value"));
        ConditionRuntime<java.util.ArrayList<String>,
                java.util.ArrayList<String>> rawStructural =
                ConditionRuntime.structural(
                        structural, "collection", java.util.Collection::size);
        ConditionRuntime<java.util.ArrayList<String>,
                java.util.ArrayList<String>> explainedStructural =
                ConditionRuntime.structural(
                        structural.because("structural"), "collection",
                        java.util.Collection::size);

        assertSame(actualCollection,
                rawStructural.evaluate(actualCollection).result());
        assertSame(actualCollection,
                explainedStructural.evaluate(actualCollection).result());
        Evaluation<java.util.ArrayList<String>> nullEvaluation =
                rawStructural.evaluate(null);
        assertEquals(Evaluation.Status.UNSATISFIED, nullEvaluation.status());
        assertEquals("collection was null", nullEvaluation.mismatch());
        assertEquals("collection to be non-empty",
                rawStructural.description().get());
        assertNull(rawStructural.explanation());
        assertEquals("structural", explainedStructural.explanation());
    }

    @Test
    void closedAdaptersPreserveNonSatisfiedOutcomes() throws Exception {
        var assertion = new AssertionError("failed");
        var cause = new IllegalStateException("broken");
        var description = (ConditionRuntime.Description) () -> "descriptor";
        var preserving = new PreservingCondition<>(new ConditionRuntime<>(
                value -> Evaluation.assertionUnsatisfied("failed", assertion),
                description, null));
        var present = new Present(new ConditionRuntime<>(
                value -> Evaluation.uncontrolled(cause), description, null));

        Evaluation<Object> preservingEvaluation =
                ConditionRuntime.preserving(preserving).evaluate(this);
        Evaluation<String> presentEvaluation = ConditionRuntime.<String>present(present)
                .evaluate(Optional.of("value"));

        assertEquals("failed", preservingEvaluation.mismatch());
        assertSame(assertion, preservingEvaluation.assertionCause());
        assertSame(cause, presentEvaluation.uncontrolledCause());
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
