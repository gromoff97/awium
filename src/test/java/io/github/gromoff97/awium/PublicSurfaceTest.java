package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSurfaceTest {

    @Test
    void sourceRemainsASingleCheckedOperation() {
        List<Method> operations = stream(Source.class.getMethods())
                .filter(method -> isAbstract(method.getModifiers())).toList();

        assertEquals(1, operations.size());
        assertEquals(List.of(Exception.class), List.of(operations.getFirst().getExceptionTypes()));
    }

    @Test
    void conditionStagesDoNotExposeRuntimeMechanicsOrFictitiousResults() {
        Set<String> runtimeMethods = Set.of("description", "explanation", "evaluatorFactory", "newEvaluator", "newSession", "preservingSession", "selectionSession");
        for (Class<?> stage : List.of(Condition.class, PreservingCondition.class,
                SelectedCondition.class, MapValueCondition.class)) {
            stream(stage.getMethods()).forEach(method ->
                    assertFalse(runtimeMethods.contains(method.getName()), method.toGenericString()));
        }
        assertTrue(Condition.class.isAssignableFrom(SelectedCondition.class));
        assertTrue(Condition.class.isAssignableFrom(PreservingCondition.class));
        assertTrue(Condition.class.isAssignableFrom(MapValueCondition.class));
        assertFalse(SelectedCondition.class.isAssignableFrom(MapValueCondition.class));
        assertTrue(Condition.class.isSealed());
    }

    @Test
    void expectedValueFactoriesDoNotBindTheObservedTypeToTheOperand() {
        Set<String> names = Set.of("equalTo", "notEqualTo", "sameAs", "notSameAs", "in", "notIn");
        List<Method> factories = stream(Conditions.class.getDeclaredMethods())
                .filter(method -> names.contains(method.getName())).toList();

        assertEquals(names.size(), factories.size());
        for (Method factory : factories) {
            assertEquals("io.github.gromoff97.awium.Condition$PreservingCondition<java.lang.Object>",
                    factory.getGenericReturnType().getTypeName(), factory.toGenericString());
        }
    }
}
