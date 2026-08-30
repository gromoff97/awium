package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedStage;
import io.github.gromoff97.awium.condition.Condition.NarrowingStage;
import io.github.gromoff97.awium.condition.Condition.PreservingStage;
import io.github.gromoff97.awium.condition.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.SelectedStage;
import io.github.gromoff97.awium.condition.ConditionStage;
import io.github.gromoff97.awium.condition.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleFinder;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    @Test
    void moduleExportsOnlyThePublicApiPackages() {
        assertEquals(Set.of(
                        "io.github.gromoff97.awium.await",
                        "io.github.gromoff97.awium.condition",
                        "io.github.gromoff97.awium.conditions",
                        "io.github.gromoff97.awium.exceptions",
                        "io.github.gromoff97.awium.results",
                        "io.github.gromoff97.awium.sources"),
                Set.copyOf(ModuleFinder.of(ArtifactContractIT.JAR)
                        .find("io.github.gromoff97.awium").orElseThrow()
                        .descriptor().exports().stream()
                        .map(export -> export.source()).toList()));
    }

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
        for (Class<?> stage : List.of(ConditionStage.class, ResultStage.class, PreservingStage.class,
                ExpectedStage.class, ExpectedSequenceStage.class, NarrowingStage.class,
                SelectedStage.class, SelectedSequenceStage.class)) {
            stream(stage.getMethods()).forEach(method ->
                    assertFalse(runtimeMethods.contains(method.getName()), method.toGenericString()));
        }
        assertFalse(ConditionStage.class.isAssignableFrom(SelectedStage.class));
        assertFalse(ConditionStage.class.isAssignableFrom(SelectedSequenceStage.class));
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
