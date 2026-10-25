package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.description;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarConditionsTest {

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        var pollingTime = new FakeTime(0);
        var waiting = await(() -> 5).usingTime(pollingTime, pollingTime);
        assertEquals(5, waiting.until(Conditions.greaterThan(4)));
        assertEquals(5, waiting.until(Conditions.atLeast(5)));
        assertEquals(5, waiting.until(Conditions.lessThan(6)));
        assertEquals(5, waiting.until(Conditions.atMost(5)));
        assertEquals(5, waiting.until(Conditions.between(5, 6)));
        assertEquals(5, waiting.until(Conditions.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() {
        var pollingTime = new FakeTime(0);
        String actual = "Ready 42";
        var waiting = await(() -> actual).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(StringConditions.nonBlank));
        assertSame(actual, waiting.until(StringConditions.contains("Ready", "42")));
        assertSame(actual, waiting.until(StringConditions.doesNotContain("failed")));
        assertSame(actual, waiting.until(StringConditions.containsIgnoringCase("READY")));
        assertSame(actual, waiting.until(StringConditions.startsWith("Ready")));
        assertSame(actual, waiting.until(StringConditions.endsWith("42")));
        assertSame(actual, waiting.until(StringConditions.matchesRegex("Ready \\d+")));
        assertSame(actual, waiting.until(StringConditions.equalToIgnoringCase("ready 42")));
        assertSame(actual, waiting.until(StringConditions.lengthBetween(1, 20)));
        assertEquals(Satisfied.class, evaluate(StringConditions.blank, " \n").getClass());
        assertEquals(Unsatisfied.class, evaluate(StringConditions.nonEmpty, "").getClass());
    }

    @Test
    void comparableConditionsCoverBoundariesAndInvalidRanges() {
        assertPreserving(Conditions.greaterThan(5), 6, 5);
        assertPreserving(Conditions.atLeast(5), 5, 4);
        assertPreserving(Conditions.lessThan(5), 4, 5);
        assertPreserving(Conditions.atMost(5), 5, 6);
        assertStatus(Conditions.between(5, 7), 5, Satisfied.class);
        assertStatus(Conditions.between(5, 7), 7, Satisfied.class);
        assertStatus(Conditions.between(5, 7), 4, Unsatisfied.class);
        assertStatus(Conditions.between(5, 7), 8, Unsatisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 6, Satisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 5, Unsatisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 7), 7, Unsatisfied.class);
        assertStatus(Conditions.between(5, 5), 5, Satisfied.class);
        assertStatus(Conditions.strictlyBetween(5, 5), 5, Unsatisfied.class);
        assertStatus(Conditions.greaterThan(5), null, Unsatisfied.class);

        assertThrows(NullPointerException.class, () -> Conditions.greaterThan(null));
        assertThrows(NullPointerException.class, () -> Conditions.between(null, 1));
        assertThrows(NullPointerException.class, () -> Conditions.between(1, null));
        assertThrows(IllegalArgumentException.class, () -> Conditions.between(2, 1));
        assertThrows(IllegalArgumentException.class, () -> Conditions.strictlyBetween(2, 1));
    }

    @Test
    void stringConditionsCoverBothTruthValuesAndBoundaries() {
        assertPreserving(StringConditions.empty, "", "x");
        assertPreserving(StringConditions.nonEmpty, "x", "");
        assertPreserving(StringConditions.blank, " \n", "x");
        assertPreserving(StringConditions.nonBlank, "x", " \n");
        assertPreserving(StringConditions.contains("a", "b"), "abc", "ac");
        assertPreserving(StringConditions.doesNotContain("x", "y"), "abc", "ayc");
        assertPreserving(StringConditions.containsIgnoringCase("READY"), "ready", "failed");
        assertStatus(StringConditions.containsIgnoringCase("ς"), "Σ", Satisfied.class);
        assertPreserving(StringConditions.containsIgnoringCase("a+b[.]"), "xA+B[.]y", "aaabxy");
        assertStatus(StringConditions.containsIgnoringCase("\uD800"), "\uD800\uDC00", Unsatisfied.class);
        assertStatus(StringConditions.containsIgnoringCase("\uD800"), "\uD800", Satisfied.class);
        assertPreserving(StringConditions.startsWith("re"), "ready", "already");
        assertPreserving(StringConditions.doesNotStartWith("fail"), "ready", "failed");
        assertPreserving(StringConditions.endsWith("dy"), "ready", "read");
        assertPreserving(StringConditions.doesNotEndWith("ed"), "ready", "failed");
        assertPreserving(StringConditions.matchesRegex(Pattern.compile("r.*y")), "ready", "failed");
        assertPreserving(StringConditions.doesNotMatchRegex("f.*d"), "ready", "failed");
        assertPreserving(StringConditions.equalToIgnoringCase("READY"), "ready", "failed");
        assertPreserving(StringConditions.notEqualToIgnoringCase("FAILED"), "ready", "failed");
        assertPreserving(StringConditions.length(5), "ready", "read");
        assertPreserving(StringConditions.lengthIsNot(4), "ready", "read");
        assertPreserving(StringConditions.lengthGreaterThan(4), "ready", "read");
        assertPreserving(StringConditions.lengthAtLeast(5), "ready", "read");
        assertPreserving(StringConditions.lengthLessThan(6), "ready", "failed");
        assertPreserving(StringConditions.lengthAtMost(5), "ready", "failed");
        assertStatus(StringConditions.length(0), "", Satisfied.class);
        assertStatus(StringConditions.length(0), null, Unsatisfied.class);
        assertStatus(StringConditions.lengthAtMost(0), "", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "read", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failed", Satisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "hey", Unsatisfied.class);
        assertStatus(StringConditions.lengthBetween(4, 6), "failure", Unsatisfied.class);
        assertStatus(StringConditions.nonBlank, null, Unsatisfied.class);
        assertEquals("string length is 5", description(StringConditions.length(5)));
        assertEquals("string length was 4",
                mismatch(evaluate(StringConditions.length(5), "read")));

        assertEquals("length must be non-negative",
                assertThrows(IllegalArgumentException.class, () -> StringConditions.length(-1)).getMessage());
        assertEquals("length range must be non-negative and ordered",
                assertThrows(IllegalArgumentException.class,
                        () -> StringConditions.lengthBetween(-1, 1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> StringConditions.lengthBetween(2, 1));
        assertThrows(IllegalArgumentException.class, StringConditions::contains);
        assertThrows(NullPointerException.class, () -> StringConditions.contains((String[]) null));
        assertThrows(NullPointerException.class, () -> StringConditions.contains("ready", null));
    }

    private static <S> void assertPreserving(PreservingCondition<? super S> condition, S matching, S mismatching) {
        assertStatus(condition, matching, Satisfied.class);
        assertStatus(condition, mismatching, Unsatisfied.class);
    }

    private static <S> void assertStatus(PreservingCondition<? super S> condition, S actual,
            Class<?> expected) {
        assertEquals(expected, evaluate(condition, actual).getClass());
    }


}
