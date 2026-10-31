package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.support.ConditionTestRuntime.result;
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

    @Test
    void nestedDefinitionsInheritTheReasonAndSnapshotReferences() {
        var reference = new Reference<>("Expected", "ready");
        var nested = new DefinedCondition<String, String>("ready", reference, actual -> satisfied(actual))
                .because("receipt is required");
        var references = new ArrayList<Reference<?>>(List.of(reference));
        var outer = new DefinedCondition<String, Integer>(nested, "receipt", references,
                () -> actual -> satisfied(actual.length()));
        references.clear();

        assertEquals("receipt: ready", outer.description());
        assertEquals("receipt is required", outer.explanation());
        assertEquals(List.of(reference), outer.references());
        assertThrows(UnsupportedOperationException.class, outer.references()::clear);
    }

    @Test
    void aDirectProbeReturnsTheDeclaredValue() throws Throwable {
        AwaitAttempt<CharSequence, Integer> length = actual -> satisfied(actual.length());
        var condition = new DefinedCondition<String, Number>("length", null, length);

        assertEquals(satisfied(5), condition.probe("ready"));
    }

    @Test
    void preservingConditionsReturnTheObservationEvenIfTheEvaluatorReturnsAnotherValue() throws Throwable {
        var actual = new StringBuilder("observed");
        var condition = new PreservingCondition<CharSequence>("accepted", null, ignored -> satisfied("different")) {};

        assertSame(actual, result(condition.newAttempt().probe(actual)));
    }

    @Test
    void comparisonsReturnTheObservationEvenIfTheEvaluatorReturnsAnotherValue() throws Throwable {
        var actual = new StringBuilder("observed");
        var condition = new ComparisonCondition<CharSequence>("comparison", new Reference<>("Expected", "value"),
                ignored -> satisfied("different")) {};

        assertSame(actual, result(condition.newAttempt().probe(actual)));
    }

    @Test
    void nestedPreservingFactoriesInitializeOncePerExecution() throws Throwable {
        var definition = new DefinedCondition<String, String>("nested", new Reference<>("Expected", "ready"), value -> satisfied(value))
                .because("nested reason");
        int[] factories = {0};
        var condition = new PreservingCondition<String>(definition, "outer", () -> {
            factories[0]++;
            int[] calls = {0};
            return actual -> ++calls[0] == 1 ? unsatisfied("retry") : satisfied("different");
        }) {};
        var execution = ConditionExecution.<String, String>bind(condition);

        assertEquals(0, factories[0]);
        assertEquals(unsatisfied("retry"), execution.probe("ready"));
        assertEquals(satisfied("ready"), execution.probe("ready"));
        assertEquals(1, factories[0]);
    }

    @Test
    void selectedDefinitionsRetainNestedMetadataAndTheDeclaredValue() {
        var definition = new DefinedCondition<Collection<String>, String>("known element", new Reference<>("Index", 0),
                actual -> satisfied(actual.iterator().next())).because("element is needed");
        var selector = new SelectedCondition<>(definition) {};

        assertEquals("ready", await(() -> List.of("ready")).until(selector));
        assertEquals(definition.description(), selector.description());
        assertEquals(definition.explanation(), selector.explanation());
        assertEquals(definition.references(), selector.references());
    }

    @Test
    void mapValueDefinitionsRetainNestedMetadataAndTheDeclaredValue() {
        var definition = new DefinedCondition<Map<String, String>, String>("known value", new Reference<>("Key", "key"),
                actual -> satisfied(actual.get("key"))).because("value is needed");
        var selector = new MapValueCondition<>(definition) {};

        assertEquals("ready", await(() -> Map.of("key", "ready")).until(selector));
        assertEquals(definition.description(), selector.description());
        assertEquals(definition.explanation(), selector.explanation());
        assertEquals(definition.references(), selector.references());
    }

    @ParameterizedTest
    @MethodSource("invalidDefinitions")
    void rejectsInvalidDefinitionsImmediately(Executable create, Class<? extends Throwable> type, String message) {
        assertEquals(message, assertThrows(type, create).getMessage());
    }

    private static List<Arguments> definitions() {
        var reference = new Reference<>("Expected", "ready");
        return List.of(
                argumentSet("typed", new DefinedCondition<String, Integer>("length", reference,
                        actual -> satisfied(actual.length())), Condition.class),
                argumentSet("preserving", new PreservingCondition<String>("accepted", reference,
                        actual -> satisfied(actual)) {}, PreservingCondition.class),
                argumentSet("comparison", new ComparisonCondition<String>("comparison", reference,
                        actual -> satisfied(actual)) {}, ComparisonCondition.class),
                argumentSet("element selector", new SelectedCondition<Collection<?>>("element", reference,
                        actual -> satisfied(actual.iterator().next())) {}, SelectedCondition.class),
                argumentSet("value selector", new MapValueCondition<Map<?, ?>>("value", reference,
                        actual -> satisfied(actual.values().iterator().next())) {}, MapValueCondition.class));
    }

    private static List<Arguments> invalidDefinitions() {
        AwaitAttempt<String, String> evaluation = actual -> satisfied(actual);
        Callable<AwaitAttempt<String, String>> factory = () -> evaluation;
        return List.of(
                argumentSet("null description", (Executable) () -> new DefinedCondition<>(null, null, evaluation),
                        NullPointerException.class, "description must not be null"),
                argumentSet("blank description", (Executable) () -> new DefinedCondition<>(" \n\t", null, evaluation),
                        IllegalArgumentException.class, "description must not be blank"),
                argumentSet("null evaluation", (Executable) () -> new DefinedCondition<String, String>("ready", null, (AwaitAttempt<String, String>) null),
                        NullPointerException.class, "evaluation must not be null"),
                argumentSet("null factory", (Executable) () -> new DefinedCondition<String, String>("ready", null, (Callable<AwaitAttempt<String, String>>) null),
                        NullPointerException.class, "evaluator factory must not be null"),
                argumentSet("null nested condition", (Executable) () -> new DefinedCondition<>(null, "nested", List.of(), factory),
                        NullPointerException.class, "condition must not be null"),
                argumentSet("null reference label", (Executable) () -> new Reference<>(null, "ready"),
                        NullPointerException.class, "reference label must not be null"),
                argumentSet("blank reference label", (Executable) () -> new Reference<>(" \n\t", "ready"),
                        IllegalArgumentException.class, "reference label must not be blank"));
    }
}
