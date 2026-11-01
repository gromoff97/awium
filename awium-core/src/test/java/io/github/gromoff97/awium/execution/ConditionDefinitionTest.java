package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.support.TestCondition;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.support.AwaitAssertions.result;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static io.github.gromoff97.awium.support.TestConditions.matching;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class ConditionDefinitionTest {
    @ParameterizedTest
    @MethodSource("definitions")
    void reasonsCreateIndependentCopiesOfTheSameFamily(AwaitCondition original, Class<?> family) {
        var first = original.because("first reason");
        var second = first.because("second reason");

        assertNotSame(original, first);
        assertNotSame(first, second);
        assertInstanceOf(family, second);
        assertNull(original.explanation());
        assertEquals("first reason", first.explanation());
        assertEquals("second reason", second.explanation());
        assertEquals(original.description(), second.description());
        assertEquals(original.references(), second.references());
    }

    @ParameterizedTest
    @MethodSource("definitions")
    void aReasonCannotBeNull(AwaitCondition condition) {
        assertThrows(NullPointerException.class, () -> condition.because(null));
    }

    @ParameterizedTest
    @MethodSource("definitions")
    void aReasonCannotBeBlank(AwaitCondition condition) {
        assertThrows(IllegalArgumentException.class, () -> condition.because(" \n\t"));
    }

    @ParameterizedTest
    @MethodSource("preservingDefinitions")
    void preservingRunsReturnTheObservationRegardlessOfTheProbeValue(AwaitCondition condition) throws Throwable {
        var actual = new StringBuilder("observed");
        var execution = ConditionExecution.<StringBuilder, StringBuilder>bind(condition);
        assertSame(actual, result(execution.probe(actual)));
    }

    @Test
    void preservingFactoriesInitializeOncePerExecution() throws Throwable {
        int[] factories = {0};
        var condition = new PreservingCondition<String>() {
            @Override public String description() { return "second observation"; }
            @Override public ConditionResult<?> probe(String actual) { throw new AssertionError("use the evaluator"); }
            @Override public AwaitAttempt<String, String> newAttempt() {
                factories[0]++;
                int[] calls = {0};
                return actual -> ++calls[0] == 1 ? unsatisfied("retry") : satisfied("different");
            }
        }.because("two probes");
        var execution = ConditionExecution.<String, String>bind(condition);

        assertEquals(0, factories[0]);
        assertEquals(unsatisfied("retry"), execution.probe("ready"));
        assertEquals(satisfied("ready"), execution.probe("ready"));
        assertEquals(1, factories[0]);
    }

    @Test
    void explainedChecksKeepTheirDirectProbe() throws Throwable {
        var check = expected("ready").because("the value is needed");
        assertEquals(satisfied("ready"), check.probe("ready"));
    }

    @Test
    void explainedChecksReturnTheOriginalEvaluator() throws Exception {
        AwaitAttempt<String, String> evaluation = actual -> satisfied(actual);
        var check = new PreservingCondition<String>() {
            @Override public String description() { return "ready"; }
            @Override public ConditionResult<?> probe(String actual) { throw new AssertionError("use the evaluator"); }
            @Override public AwaitAttempt<String, String> newAttempt() { return evaluation; }
        };
        assertSame(evaluation, check.because("the value is needed").newAttempt());
    }

    @Test
    void referenceDescriptionsGuardUserRendering() {
        var value = new Object() {
            @Override public String toString() { throw new IllegalStateException("cannot print"); }
        };
        assertEquals("Expected=<unprintable>", new Reference<>("Expected", value).toString());
    }

    @Test
    void referenceLabelsCannotBeNull() {
        assertThrows(NullPointerException.class, () -> new Reference<>(null, "ready"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void referenceLabelsCannotBeBlank(String label) {
        assertThrows(IllegalArgumentException.class, () -> new Reference<>(label, "ready"));
    }

    @Test
    void selectedCopiesKeepTheirResultAndOriginalDefinition() {
        var selector = new First().because("element is needed");
        assertEquals("ready", await(() -> List.of("ready")).until(selector));
        assertEquals("element is needed", selector.explanation());
    }

    @Test
    void mapValueCopiesKeepTheirResultAndOriginalDefinition() {
        var selector = new Value().because("value is needed");
        assertEquals("ready", await(() -> Map.of("key", "ready")).until(selector));
        assertEquals("value is needed", selector.explanation());
    }

    private static List<Arguments> definitions() {
        return List.of(
                argumentSet("typed", new TestCondition<String, Integer>("length", new Reference<>("Expected", "ready"),
                        actual -> satisfied(actual.length())), Condition.class),
                argumentSet("preserving", matching(actual -> true), PreservingCondition.class),
                argumentSet("comparison", expected("ready"), ComparisonCondition.class),
                argumentSet("element selector", new First(), SelectedCondition.class),
                argumentSet("value selector", new Value(), MapValueCondition.class));
    }

    private static List<Arguments> preservingDefinitions() {
        return List.of(
                argumentSet("check", new PreservingCondition<CharSequence>() {
                    @Override public String description() { return "accepted"; }
                    @Override public ConditionResult<?> probe(CharSequence actual) { return satisfied("different"); }
                }),
                argumentSet("comparison", new ComparisonCondition<CharSequence>() {
                    @Override public String description() { return "accepted"; }
                    @Override public ConditionResult<?> probe(Object actual) { return satisfied("different"); }
                }));
    }

    private record First() implements SelectedCondition<Collection<?>> {
        @Override public String description() { return "first element"; }
        @Override public ConditionResult<?> probe(Collection<?> actual) { return satisfied(actual.iterator().next()); }
    }

    private record Value() implements MapValueCondition<Map<?, ?>> {
        @Override public String description() { return "first value"; }
        @Override public ConditionResult<?> probe(Map<?, ?> actual) { return satisfied(actual.values().iterator().next()); }
    }
}
