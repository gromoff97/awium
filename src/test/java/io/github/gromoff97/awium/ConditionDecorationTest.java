package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionSupport.captured;
import static io.github.gromoff97.awium.ConditionTestRuntime.explanation;
import static io.github.gromoff97.awium.Conditions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionDecorationTest {

    @Test
    void everyConditionKindFormatsItsExplanationEagerly() {
        PreservingCondition<Object> condition = condition(
                "custom condition", ConditionResult::satisfied);
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
    void replacingAnExplanationLeavesTheOriginalAndConstantsUnchanged() {
        var conditions = List.<Condition<?, ?>>of(
                instanceOf(String.class),
                captured(equalTo("first"), equalTo("last")),
                captured(CollectionConditions.first, CollectionConditions.last));
        var replacements = conditions.stream()
                .map(condition -> condition.because("old").because("new %d", 42)).toList();

        for (var condition : conditions) assertNull(condition.explanation());
        for (var replacement : replacements) assertEquals("new 42", replacement.explanation());
        var custom = condition("custom", ConditionResult::satisfied);
        assertEquals("new 42", explanation(custom.because("old").because("new %d", 42)));
        assertNull(explanation(custom));
        assertEquals("new 42", explanation(isNotNull.because("old").because("new %d", 42)));
        assertNull(explanation(isNotNull));
        var comparison = equalTo("ready");
        assertEquals("new 42", explanation(comparison.because("old").because("new %d", 42)));
        assertNull(explanation(comparison));
        var first = OptionalConditions.present.because("first");
        var second = first.because("second");
        assertEquals("first", explanation(first));
        assertEquals("second", explanation(second));
        assertNull(explanation(OptionalConditions.present));
        var value = MapConditions.valueFor("key");
        var explained = value.because("old").because("new %d", 42);
        assertNull(explanation(value));
        assertEquals("new 42", explanation(explained));
    }

    @Test
    void nestedConditionsRetainTheirExplanation() {
        var nested = matches((String actual) -> true).because("business reason");

        assertEquals("business reason", explanation(OptionalConditions.hasValue(nested)));
        assertEquals("business reason", explanation(MapConditions.valueFor("key", nested)));
    }

    @Test
    void explanationValidationHappensBeforeEvaluation() {
        PreservingCondition<Object> condition = condition("custom condition",
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
    void missingConditionsAreRejectedBeforeReadingTheSource() {
        Callable<String> source = () -> {
            throw new AssertionError("source must not be read");
        };
        var waiting = Await.await(source);
        assertEquals("condition must not be null",
                assertThrows(NullPointerException.class, () -> waiting.until((PreservingCondition<String>) null)).getMessage());
        assertEquals("condition must not be null",
                assertThrows(NullPointerException.class, () -> waiting.tryUntil((PreservingCondition<String>) null)).getMessage());
    }

    @Test
    void specializedConditionsCannotBypassExplanationValidation() {
        assertValidation("explanation", IllegalArgumentException.class,
                () -> asserted((Object actual) -> {}).because(" \n "));
        assertValidation("explanation", NullPointerException.class,
                () -> asserted((Object actual) -> {}).because((String) null));
        assertValidation("explanation", NullPointerException.class,
                () -> equalTo("ready").because((String) null));
        assertValidation("explanation", IllegalArgumentException.class,
                () -> equalTo("ready").because("%s", " "));
        assertValidation("explanation", IllegalArgumentException.class,
                () -> MapConditions.valueFor("key").because("%s", " "));
        assertValidation("explanation", NullPointerException.class,
                () -> MapConditions.valueFor("key").because((String) null));
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

    private static void assertValidation(String context,
            Class<? extends Throwable> type, org.junit.jupiter.api.function.Executable action) {
        assertTrue(assertThrows(type, action).getMessage().contains(context));
    }

}
