package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSurfaceTest {

    @Test
    void acceptsJdkCallableOnTheEnteringThreadWithItsConcreteReturnType() {
        Thread caller = Thread.currentThread();
        Callable<String> source = () -> {
            assertSame(caller, Thread.currentThread());
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
            stream(stage.getMethods()).forEach(method ->
                    assertFalse(excludedMethods.contains(method.getName()), method.toGenericString()));
        }
        assertTrue(Condition.class.isAssignableFrom(ComparisonCondition.class));
        assertTrue(Condition.class.isAssignableFrom(SelectedCondition.class));
        assertTrue(Condition.class.isAssignableFrom(PreservingCondition.class));
        assertTrue(Condition.class.isAssignableFrom(MapValueCondition.class));
        assertFalse(SelectedCondition.class.isAssignableFrom(MapValueCondition.class));
        assertTrue(Condition.class.isSealed());
    }

    @Test
    void terminalsAcceptExactlyOneConditionWithoutVarargs() {
        for (var stage : List.<Class<?>>of(ValueAwait.class, SelectionAwait.class, MapAwait.class)) {
            for (var method : stage.getMethods()) {
                if (method.getName().equals("until") || method.getName().equals("tryUntil")) {
                    assertFalse(method.isVarArgs(), method.toGenericString());
                    assertEquals(1, method.getParameterCount(), method.toGenericString());
                    assertTrue(Condition.class.isAssignableFrom(method.getParameterTypes()[0]), method.toGenericString());
                }
            }
        }
    }

    @Test
    void constantConditionsHaveNoZeroArgumentFactoryAliases() {
        for (var catalog : List.of(Conditions.class, OptionalConditions.class, CollectionConditions.class,
                MapConditions.class, StringConditions.class)) {
            for (var field : catalog.getFields()) {
                assertTrue(stream(catalog.getDeclaredMethods()).noneMatch(method ->
                        method.getName().equals(field.getName()) && method.getParameterCount() == 0),
                        field.toGenericString());
            }
        }
    }

}
