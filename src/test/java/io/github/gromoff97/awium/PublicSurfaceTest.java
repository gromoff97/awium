package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static java.lang.reflect.Modifier.isFinal;
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

        String value = Await.await(source).until(Conditions.isNotNull.because("standard checked supplier"));

        assertEquals("ready", value);
    }

    @Test
    void conditionsDoNotExposeRuntimeMechanicsOrAlternativeFactories() {
        Set<String> excludedMethods = Set.of("evaluatorFactory", "newEvaluator", "newAttempt",
                "preservingAttempt", "selectionAttempt", "yields", "preserving", "captured");
        for (Class<?> stage : List.of(Conditions.class, Condition.class, PreservingCondition.class, ComparisonCondition.class,
                SelectedCondition.class, MapValueCondition.class)) {
            stream(stage.getMethods()).forEach(method ->
                    assertFalse(excludedMethods.contains(method.getName()), method.toGenericString()));
        }
        assertFalse(Condition.class.isAssignableFrom(ComparisonCondition.class));
        assertFalse(Condition.class.isAssignableFrom(SelectedCondition.class));
        assertFalse(Condition.class.isAssignableFrom(PreservingCondition.class));
        assertFalse(Condition.class.isAssignableFrom(MapValueCondition.class));
        assertFalse(SelectedCondition.class.isAssignableFrom(MapValueCondition.class));
        assertTrue(isFinal(Condition.class.getModifiers()));
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
