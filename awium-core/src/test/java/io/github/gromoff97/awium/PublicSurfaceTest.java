package io.github.gromoff97.awium;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static io.github.gromoff97.awium.TestConditions.accepted;
import static java.lang.Thread.currentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        String value = await(source).until(accepted);

        assertEquals("ready", value);
    }

    @Test
    void conditionsExposeExtensionHooksWithoutAlternativeFactories() {
        Set<String> excludedMethods = Set.of("definition", "evaluatorFactory", "newEvaluator",
                "preservingAttempt", "selectionAttempt", "yields", "preserving", "bind");
        for (Class<?> stage : List.of(AwaitCondition.class, CollectingCondition.class,
                InputCondition.class, Condition.class, PreservingCondition.class, ComparisonCondition.class,
                SelectedCondition.class, MapValueCondition.class, ApplicableCondition.class)) {
            assertThat(stage.getMethods()).noneMatch(method -> excludedMethods.contains(method.getName()));
        }
        assertThat(AwaitCondition.class).isInterface().isAssignableFrom(ComparisonCondition.class, SelectedCondition.class,
                PreservingCondition.class, MapValueCondition.class);
        assertFalse(SelectedCondition.class.isAssignableFrom(MapValueCondition.class));
        assertTrue(Condition.class.isInterface());
        assertFalse(Condition.class.isSealed());
        for (var family : List.of(SelectedCondition.class, MapValueCondition.class, CollectingCondition.class)) {
            assertFalse(family.isSealed(), family.getName());
        }
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void terminalsAcceptExactlyOneConditionWithoutVarargs(Class<?> stage) {
        assertThat(stage.getMethods()).noneMatch(method -> method.getName().equals("tryUntil"))
                .filteredOn(method -> method.getName().equals("until")).isNotEmpty().allSatisfy(method -> {
                    assertFalse(method.isVarArgs(), method.toGenericString());
                    assertEquals(1, method.getParameterCount(), method.toGenericString());
                    assertTrue(AwaitCondition.class.isAssignableFrom(method.getParameterTypes()[0]), method.toGenericString());
                });
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void bothEntryPointsExposeOnlySharedTimingSettings(Class<?> stage) {
        var settings = Set.of("every", "upTo", "persisting");
        var hidden = Set.of("historyTail", "usingTime");
        assertThat(stage.getMethods()).noneMatch(method -> hidden.contains(method.getName()))
                .filteredOn(method -> settings.contains(method.getName()) && !method.isBridge())
                .allSatisfy(method -> assertTrue(stage.isAssignableFrom(method.getReturnType()), method.toGenericString()));
    }
}
