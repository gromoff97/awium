package io.github.gromoff97.awium;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import static io.github.gromoff97.awium.Condition.ApplicableCondition;
import static io.github.gromoff97.awium.Condition.ComparisonCondition;
import static io.github.gromoff97.awium.Condition.MapValueCondition;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.SelectedCondition;
import static io.github.gromoff97.awium.Await.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.lang.Thread.currentThread;

class PublicSurfaceTest {

    private static final List<Class<?>> STAGES = List.of(ValueAwait.class, SelectionAwait.class, MapAwait.class,
            DiagnosticAwait.class, DiagnosticSelectionAwait.class, DiagnosticMapAwait.class);

    @Test
    void acceptsJdkCallableOnTheEnteringThreadWithItsConcreteReturnType() {
        Thread caller = currentThread();
        Callable<String> source = () -> {
            assertSame(caller, currentThread());
            return "ready";
        };

        String value = await(source).until(Conditions.isNotNull);

        assertEquals("ready", value);
    }

    @Test
    void conditionsDoNotExposeRuntimeMechanicsOrAlternativeFactories() {
        Set<String> excludedMethods = Set.of("definition", "evaluatorFactory", "newEvaluator", "newAttempt",
                "preservingAttempt", "selectionAttempt", "yields", "preserving");
        for (Class<?> stage : List.of(Conditions.class, Condition.class, Condition.Check.class, Condition.Captured.class,
                Condition.InputCondition.class, Condition.Typed.class, PreservingCondition.class, ComparisonCondition.class,
                SelectedCondition.class, MapValueCondition.class, ApplicableCondition.class,
                Condition.CapturedSource.class)) {
            assertThat(stage.getMethods()).noneMatch(method -> excludedMethods.contains(method.getName()));
        }
        assertThat(Condition.class).isSealed().isAssignableFrom(ComparisonCondition.class, SelectedCondition.class,
                PreservingCondition.class, MapValueCondition.class);
        assertFalse(SelectedCondition.class.isAssignableFrom(MapValueCondition.class));
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void terminalsAcceptExactlyOneConditionWithoutVarargs(Class<?> stage) {
        for (var method : stage.getMethods()) {
            assertFalse(method.getName().equals("tryUntil"), method.toGenericString());
            if (method.getName().equals("until")) {
                assertFalse(method.isVarArgs(), method.toGenericString());
                assertEquals(1, method.getParameterCount(), method.toGenericString());
                assertTrue(Condition.class.isAssignableFrom(method.getParameterTypes()[0]), method.toGenericString());
            }
        }
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void bothEntryPointsExposeOnlySharedTimingSettings(Class<?> stage) {
        var settings = Set.of("every", "upTo", "persisting", "usingTime");
        for (var method : stage.getMethods()) {
            assertFalse(method.getName().equals("historyTail"), method.toGenericString());
            if (settings.contains(method.getName()) && !method.isBridge()) {
                assertTrue(stage.isAssignableFrom(method.getReturnType()), method.toGenericString());
            }
        }
    }

    @Test
    void constantConditionsHaveNoZeroArgumentFactoryAliases() {
        for (var catalog : List.of(Conditions.class, OptionalConditions.class, CollectionConditions.class,
                MapConditions.class, StringConditions.class)) {
            for (var field : catalog.getFields()) {
                assertThat(catalog.getDeclaredMethods()).as(field.toGenericString()).noneMatch(method ->
                        method.getName().equals(field.getName()) && method.getParameterCount() == 0);
            }
        }
    }

}
