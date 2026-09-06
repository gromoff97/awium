package io.github.gromoff97.awium.condition;

import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.captured;

import io.github.gromoff97.awium.CompilationSupport;
import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditions.CollectionConditions;
import io.github.gromoff97.awium.conditions.MapConditions;
import io.github.gromoff97.awium.conditions.OptionalConditions;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.*;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.condition.ConditionTestRuntime.explanation;
import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConditionDecorationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void customConditionFactoryCreatesFreshEvaluatorForEveryWait() {
        var factories = new AtomicInteger();
        Condition<String, String> condition = conditionFactory("first evaluation succeeds", () -> {
            factories.incrementAndGet();
            var evaluations = new AtomicInteger();
            return actual -> evaluations.incrementAndGet() == 1
                    ? satisfied(actual) : unsatisfied("evaluation was repeated");
        });
        var time = new FakeTime(0);

        assertEquals("ready", timedAwait(() -> "ready", defaults(), time, time).until(condition));
        assertEquals("ready", timedAwait(() -> "ready", defaults(), time, time).until(condition));
        assertEquals(2, factories.get());
    }

    @Test
    void customPreservingConditionParticipatesInPreservingSequences() {
        PreservingCondition<String> custom = preserving("value is ready", actual -> actual.equals("ready")
                ? satisfied(actual) : unsatisfied("value was not ready"));
        var time = new FakeTime(0);

        List<String> captured = timedAwait(() -> "ready", defaults(), time, time).until(custom,
                matches(actual -> actual.equals("ready")));

        assertEquals(List.of("ready", "ready"), captured);
    }

    @Test
    void customPreservingFactoryCreatesFreshEvaluatorForEveryWait() {
        var factories = new AtomicInteger();
        PreservingCondition<String> condition = preservingFactory("first evaluation succeeds", () -> {
            factories.incrementAndGet();
            var evaluations = new AtomicInteger();
            return actual -> evaluations.incrementAndGet() == 1
                    ? satisfied(actual) : unsatisfied("evaluation was repeated");
        });
        var time = new FakeTime(0);

        assertEquals("ready", timedAwait(() -> "ready", defaults(), time, time).until(condition));
        assertEquals("ready", timedAwait(() -> "ready", defaults(), time, time).until(condition));
        assertEquals(2, factories.get());
    }

    @Test
    void plainAndExplainedConditionsKeepTheSameType() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditions.Conditions.condition;
                import io.github.gromoff97.awium.condition.ConditionEvaluation;
                import io.github.gromoff97.awium.condition.Condition;
                final class Contract {
                    void check() {
                        accept(condition("plain", ConditionEvaluation::satisfied));
                        accept(condition("explained", ConditionEvaluation::satisfied).because("reason"));
                    }
                    void accept(Condition<Object, Object> condition) {}
                }
                """));
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditions.Conditions.condition;
                import io.github.gromoff97.awium.condition.ConditionEvaluation;
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
    void replacingAnExplanationLeavesTheOriginalAndConstantsUnchanged() {
        var conditions = List.<AwaitCondition>of(
                condition("custom", ConditionEvaluation::satisfied),
                isNotNull, equalTo("ready"), instanceOf(String.class),
                OptionalConditions.present, captured(equalTo("first"), equalTo("last")),
                captured(CollectionConditions.first, CollectionConditions.last));
        var replacements = List.<AwaitCondition>of(
                condition("custom", ConditionEvaluation::satisfied).because("old").because("new %d", 42),
                isNotNull.because("old").because("new %d", 42),
                equalTo("ready").because("old").because("new %d", 42),
                instanceOf(String.class).because("old").because("new %d", 42),
                OptionalConditions.present.because("old").because("new %d", 42),
                captured(equalTo("first"), equalTo("last")).because("old").because("new %d", 42),
                captured(CollectionConditions.first, CollectionConditions.last).because("old").because("new %d", 42));

        for (var condition : conditions) assertNull(explanation(condition));
        for (var replacement : replacements) assertEquals("new 42", explanation(replacement));
        var first = OptionalConditions.present.because("first");
        var second = first.because("second");
        assertEquals("first", explanation(first));
        assertEquals("second", explanation(second));
    }

    @Test
    void nestedConditionsRetainTheirExplanation() {
        var nested = matches((String actual) -> true).because("business reason");

        assertEquals("business reason", explanation(OptionalConditions.hasValue(nested)));
        assertEquals("business reason", explanation(MapConditions.valueFor("key", nested)));
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
