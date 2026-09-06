package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.Condition.ExpectedCondition;
import io.github.gromoff97.awium.condition.Condition.NarrowingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
        Set<String> runtimeMethods = Set.of("description", "explanation", "evaluatorFactory", "newEvaluator");
        for (Class<?> stage : List.of(Condition.class, PreservingCondition.class,
                ExpectedCondition.class, NarrowingCondition.class, SelectedCondition.class)) {
            stream(stage.getMethods()).forEach(method ->
                    assertFalse(runtimeMethods.contains(method.getName()), method.toGenericString()));
        }
        assertFalse(Condition.class.isAssignableFrom(SelectedCondition.class));
    }

    @Test
    void expectedValueFactoriesUseGenericOperands() {
        Set<String> names = Set.of("equalTo", "notEqualTo", "sameAs", "notSameAs", "in", "notIn");
        List<Method> factories = stream(Conditions.class.getDeclaredMethods())
                .filter(method -> names.contains(method.getName())).toList();

        assertEquals(names.size(), factories.size());
        for (Method factory : factories) {
            assertEquals(1, factory.getTypeParameters().length, factory.toGenericString());
            Type operand = factory.getGenericParameterTypes()[0];
            assertFalse(operand == Object.class, factory.toGenericString());
            assertTrue(operand instanceof TypeVariable<?> || operand instanceof GenericArrayType,
                    factory.toGenericString());
        }
    }
}
