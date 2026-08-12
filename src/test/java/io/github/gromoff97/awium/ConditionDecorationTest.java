package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.Evaluation.*;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IllegalFormatException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ConditionDecorationTest {

    @Test
    void everyConditionKindFormatsItsExplanationEagerly() {
        Condition<Object, Object> condition = condition(
                "custom condition", Evaluation::satisfied);
        var preserving = PreservingCondition.of(runtime());
        var present = PresentCondition.of(new RuntimeCondition<>(
                value -> satisfied(value.orElse(null)), () -> "present", null));

        assertEquals("the value must be ready",
                condition.because("the value must be ready").explanation());
        assertEquals("attempt 3",
                condition.because("attempt %d", 3).explanation());
        assertEquals("preserving",
                preserving.because("preserving").explanation());
        assertEquals("present value",
                present.because("present %s", "value").explanation());
        assertEquals("structural value",
                nonEmpty.because("structural %s", "value").explanation());
    }

    @Test
    void explanationValidationHappensBeforeEvaluation() {
        Condition<Object, Object> condition = condition("custom condition",
                actual -> {
                    throw new AssertionError("condition evaluated");
                });

        assertValidation("explanation", NullPointerException.class,
                () -> condition.because((String) null));
        assertValidation("explanation", IllegalArgumentException.class,
                () -> condition.because(" \n "));
        assertValidation("format", NullPointerException.class,
                () -> condition.because(null, 1));
        assertValidation("arguments", NullPointerException.class,
                () -> condition.because("%s", (Object[]) null));
        assertValidation("explanation", IllegalArgumentException.class,
                () -> condition.because("%s", " "));
        assertThrows(IllegalFormatException.class, () -> condition.because("%q", 1));
        assertThrows(IllegalStateException.class,
                () -> condition.because("%s", new Object() {
                    @Override
                    public String toString() {
                        throw new IllegalStateException();
                    }
                }));
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

    private static RuntimeCondition<Object, Object> runtime() {
        return new RuntimeCondition<>(Evaluation::satisfied, () -> "condition", null);
    }

    private static <T extends Throwable> void assertValidation(String context,
            Class<T> type, org.junit.jupiter.api.function.Executable action) {
        assertTrue(assertThrows(type, action).getMessage().contains(context));
    }
}
