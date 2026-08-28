package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.*;
import static io.github.gromoff97.awium.fluent.Conditions.*;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.explanation;

import io.github.gromoff97.awium.evaluation.*;
import io.github.gromoff97.awium.fluent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IllegalFormatException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConditionDecorationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void plainAndExplainedConditionsShareOneNonDecoratableStage() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.fluent.Conditions.condition;
                import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
                import io.github.gromoff97.awium.fluent.ConditionStage;
                final class Contract {
                    void check() {
                        accept(condition("plain", ConditionEvaluation::satisfied));
                        accept(condition("explained", ConditionEvaluation::satisfied).because("reason"));
                    }
                    void accept(ConditionStage<Object, Object> condition) {}
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.fluent.Conditions.condition;
                import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
                final class Contract {
                    void check() {
                        condition("condition", ConditionEvaluation::satisfied).because("first").because("second");
                    }
                }
                """));
    }

    @Test
    void everyConditionKindFormatsItsExplanationEagerly() {
        Condition<Object, Object> condition = condition(
                "custom condition", ConditionEvaluation::satisfied);
        var preserving = asserted(actual -> {});
        var selected = OptionalConditions.present;

        assertEquals("the value must be ready",
                explanation(condition.because("the value must be ready")));
        assertEquals("attempt 3",
                explanation(condition.because("attempt %d", 3)));
        assertEquals("preserving",
                explanation(preserving.because("preserving")));
        assertEquals("selected value",
                explanation(selected.because("selected %s", "value")));
        assertEquals("collection value",
                explanation(CollectionConditions.nonEmpty.because("collection %s", "value")));
        assertEquals("single element",
                explanation(CollectionConditions.single.because("single %s", "element")));
        assertEquals("map value",
                explanation(MapConditions.nonEmpty.because("map %s", "value")));
        assertEquals("single entry",
                explanation(MapConditions.singleEntry.because("single %s", "entry")));
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
    void specializedConditionsCannotBypassExplanationValidation() {
        assertValidation("explanation", IllegalArgumentException.class,
                () -> asserted((Object actual) -> {}).because(" \n "));
        assertValidation("explanation", NullPointerException.class,
                () -> asserted((Object actual) -> {}).because((String) null));
    }

    @Test
    void formattedExplanationsUseRootLocale() {
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
        try {
            assertEquals("1.5",
                    explanation(asserted((Object actual) -> {}).because("%.1f", 1.5)));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    private static <T extends Throwable> void assertValidation(String context,
            Class<T> type, org.junit.jupiter.api.function.Executable action) {
        assertTrue(assertThrows(type, action).getMessage().contains(context));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}
