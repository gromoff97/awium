package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.SelectionAwait;
import io.github.gromoff97.awium.MapAwait;
import io.github.gromoff97.awium.DiagnosticAwait;
import io.github.gromoff97.awium.DiagnosticSelectionAwait;
import io.github.gromoff97.awium.DiagnosticMapAwait;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSurfaceTest {

    private static final List<Class<?>> STAGES = List.of(ValueAwait.class, SelectionAwait.class, MapAwait.class,
            DiagnosticAwait.class, DiagnosticSelectionAwait.class, DiagnosticMapAwait.class);

    @ParameterizedTest
    @ValueSource(classes = {AwaitCondition.class, CollectingCondition.class, InputCondition.class, Condition.class,
            PreservingCondition.class, ComparisonCondition.class, SelectedCondition.class, MapValueCondition.class,
            ApplicableCondition.class})
    void conditionFamiliesHaveNoAlternativeFactories(Class<?> family) {
        Set<String> excludedMethods = Set.of("definition", "evaluatorFactory", "newEvaluator",
                "preservingAttempt", "selectionAttempt", "yields", "preserving", "bind");
        assertThat(family.getMethods()).noneMatch(method -> excludedMethods.contains(method.getName()));
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
                .allSatisfy(method -> assertTrue(stage.isAssignableFrom(method.getReturnType()), method.toGenericString()))
                .extracting(method -> method.getName()).containsExactlyInAnyOrderElementsOf(settings);
    }
}
