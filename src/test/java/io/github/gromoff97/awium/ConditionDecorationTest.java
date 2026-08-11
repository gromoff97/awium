package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
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
                return satisfied(value);
            }

            @Override
            public String description() {
                return "custom condition";
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
        RuntimeCondition<Object, Object> preservingRuntime = runtime();
        RuntimeCondition<Optional<?>, Object> presentRuntime = new RuntimeCondition<>(
                value -> satisfied(value.orElse(null)), () -> "present", null);
        var preserving = PreservingCondition.of(preservingRuntime);
        var present = PresentCondition.of(presentRuntime);
        var structural = nonEmpty;

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
        Condition<Object, Object> condition = condition("custom condition",
                actual -> {
                    evaluations[0]++;
                    return satisfied(actual);
                });

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
        assertThrows(IllegalStateException.class,
                () -> condition.because("%s", new ThrowingRenderer()));
        assertEquals(0, evaluations[0]);
    }

    @Test
    void formattedExplanationsUseRootLocale() {
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
        try {
            assertEquals("1.5", PreservingCondition.of(runtime())
                    .because("%.1f", 1.5).explanation());
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    @Test
    void openAdaptersKeepTheDelegateSemanticsAndOptionalExplanation()
            throws Exception {
        var evaluations = new int[1];
        Condition<Object, String> condition = condition("named condition",
                actual -> {
                    evaluations[0]++;
                    return satisfied("selected");
                });

        RuntimeCondition<Object, String> raw = open(condition);
        RuntimeCondition<Object, String> explained = open(
                condition.because("because value"));

        assertEquals("selected", raw.evaluate(this).result());
        assertEquals("selected", explained.evaluate(this).result());
        assertEquals(2, evaluations[0]);
        assertEquals("named condition", raw.description().get());
        assertNull(raw.explanation());
        assertEquals("because value", explained.explanation());

        Condition<Object, Object> nullEvaluation = condition(
                "custom condition", actual -> null);
        assertNull(open(nullEvaluation).evaluate(this));
    }

    @Test
    void closedAdaptersRecoverTheirExactResultContractsAndExplanations()
            throws Exception {
        var description = (java.util.function.Supplier<String>) () -> "descriptor";
        var actual = new StringBuilder("actual");
        var preserving = PreservingCondition.of(new RuntimeCondition<>(
                value -> satisfied(new Object()), description, null));

        RuntimeCondition<StringBuilder, StringBuilder> rawPreserving =
                preserving(preserving);
        RuntimeCondition<StringBuilder, StringBuilder> explainedPreserving =
                preserving(preserving.because("preserving"));

        assertSame(actual, rawPreserving.evaluate(actual).result());
        assertSame(actual, explainedPreserving.evaluate(actual).result());
        assertSame(description, rawPreserving.description());
        assertNull(rawPreserving.explanation());
        assertEquals("preserving", explainedPreserving.explanation());

        var present = PresentCondition.of(new RuntimeCondition<>(
                value -> satisfied(value.orElse(null)), description, null));
        RuntimeCondition<Optional<String>, String> rawPresent =
                present(present);
        RuntimeCondition<Optional<String>, String> explainedPresent =
                present(present.because("present"));

        assertEquals("value", rawPresent.evaluate(Optional.of("value")).result());
        assertEquals("value",
                explainedPresent.evaluate(Optional.of("value")).result());
        assertSame(description, rawPresent.description());
        assertNull(rawPresent.explanation());
        assertEquals("present", explainedPresent.explanation());

        var structural = nonEmpty;
        var actualCollection = new java.util.ArrayList<>(java.util.List.of("value"));
        RuntimeCondition<java.util.ArrayList<String>,
                java.util.ArrayList<String>> rawStructural =
                structural(
                        structural, "collection", java.util.Collection::size);
        RuntimeCondition<java.util.ArrayList<String>,
                java.util.ArrayList<String>> explainedStructural =
                structural(
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
        var description = (java.util.function.Supplier<String>) () -> "descriptor";
        var preserving = PreservingCondition.of(new RuntimeCondition<>(
                value -> assertionUnsatisfied("failed", assertion),
                description, null));
        var present = PresentCondition.of(new RuntimeCondition<>(
                value -> uncontrolled(cause), description, null));

        Evaluation<Object> preservingEvaluation =
                preserving(preserving).evaluate(this);
        Evaluation<String> presentEvaluation = RuntimeCondition.<String>present(present)
                .evaluate(Optional.of("value"));

        assertEquals("failed", preservingEvaluation.mismatch());
        assertSame(assertion, preservingEvaluation.assertionCause());
        assertSame(cause, presentEvaluation.uncontrolledCause());
    }

    private static RuntimeCondition<Object, Object> runtime() {
        return new RuntimeCondition<>(Evaluation::satisfied, () -> "condition", null);
    }

    private static final class ThrowingRenderer {
        @Override
        public String toString() {
            throw new IllegalStateException();
        }
    }
}
