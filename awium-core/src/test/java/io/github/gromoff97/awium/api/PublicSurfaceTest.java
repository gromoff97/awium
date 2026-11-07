package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Await;
import org.junit.jupiter.api.Test;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.DiagnosticAwait;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSurfaceTest {

    private static final List<Class<?>> STAGES = List.of(ValueAwait.class, DiagnosticAwait.class);

    @Test
    void theConditionContractHasNoParentInterfaces() {
        assertThat(Condition.class.getInterfaces()).isEmpty();
    }

    @Test
    void eachCoreEntryPointAcceptsOneCallableSource() {
        assertThat(Await.class.getDeclaredMethods()).filteredOn(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())).hasSize(2).allSatisfy(method -> {
            assertEquals(1, method.getParameterCount());
            assertEquals(java.util.concurrent.Callable.class, method.getParameterTypes()[0]);
        });
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void terminalsAcceptExactlyOneConditionWithoutVarargs(Class<?> stage) {
        assertThat(stage.getMethods()).noneMatch(method -> method.getName().equals("tryUntil"))
                .filteredOn(method -> method.getName().equals("until")).hasSize(1).allSatisfy(method -> {
                    assertFalse(method.isVarArgs(), method.toGenericString());
                    assertEquals(1, method.getParameterCount(), method.toGenericString());
                    assertTrue(Condition.class.isAssignableFrom(method.getParameterTypes()[0]), method.toGenericString());
                });
    }

    @ParameterizedTest
    @FieldSource("STAGES")
    void bothEntryPointsExposeOnlySharedTimingSettings(Class<?> stage) {
        var settings = Set.of("every", "upTo", "persisting", "handling");
        var hidden = Set.of("historyTail", "usingTime");
        assertThat(stage.getMethods()).noneMatch(method -> hidden.contains(method.getName()))
                .filteredOn(method -> settings.contains(method.getName()) && !method.isBridge())
                .allSatisfy(method -> assertTrue(stage.isAssignableFrom(method.getReturnType()), method.toGenericString()))
                .extracting(method -> method.getName()).containsExactlyInAnyOrderElementsOf(settings);
    }
}
