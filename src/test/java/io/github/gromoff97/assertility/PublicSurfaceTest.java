package io.github.gromoff97.assertility;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    @Test
    void taskOnePublicTypesAreTopLevel() {
        for (Class<?> type : publicTypes()) {
            assertTrue(isPublic(type.getModifiers()), type.getName());
            assertNull(type.getEnclosingClass(), type.getName());
            assertEquals("io.github.gromoff97.assertility", type.getPackageName());
        }
    }

    @Test
    void conditionIsTheOnlyOpenConditionForm() throws ReflectiveOperationException {
        assertTrue(isPublic(Condition.class.getModifiers()));
        assertTrue(isAbstract(Condition.class.getModifiers()));
        Constructor<?> constructor = Condition.class.getDeclaredConstructor();
        assertTrue(isProtected(constructor.getModifiers()));
        assertTrue(isFinal(Condition.class.getMethod("because", String.class)
                .getModifiers()));
        assertTrue(isFinal(Condition.class.getMethod(
                "because", String.class, Object[].class).getModifiers()));

        for (Class<?> type : closedConditionTypes()) {
            assertTrue(isPublic(type.getModifiers()), type.getName());
            assertTrue(isFinal(type.getModifiers()), type.getName());
            assertNoPublicOrProtectedConstructor(type);
        }
    }

    @Test
    void explainedFormsCannotBeDecoratedAgain() {
        for (Class<?> type : explainedTypes()) {
            assertFalse(List.of(type.getMethods()).stream()
                    .map(Method::getName)
                    .anyMatch("because"::equals), type.getName());
        }
    }

    @Test
    void callbackAndSourceTypesAreCheckedExceptionCapableSams() throws Exception {
        ThrowingConsumer<String> consumer = value -> {
            if (value.isEmpty()) {
                throw new Exception("empty");
            }
        };
        ThrowingFunction<String, Integer> function = String::length;
        AwaitSources.Source<String> source = () -> "source";
        AwaitSources.OptionalSource<String> optionalSource = () -> Optional.of("optional");
        AwaitSources.CollectionSource<String, Collection<String>> collectionSource =
                () -> List.of("collection");
        AwaitSources.SequencedCollectionSource<String, SequencedCollection<String>>
                sequencedSource = () -> List.of("sequenced");
        AwaitSources.MapSource<String, Integer, Map<String, Integer>> mapSource =
                () -> Map.of("map", 1);

        consumer.accept("value");
        assertEquals(5, function.apply("value"));
        assertEquals("source", source.get());
        assertEquals("optional", optionalSource.get().orElseThrow());
        assertEquals("collection", collectionSource.get().iterator().next());
        assertEquals("sequenced", sequencedSource.get().getFirst());
        assertEquals(1, mapSource.get().get("map"));
    }

    @Test
    void awaitSourcesIsAnUninstantiableNamespace() throws ReflectiveOperationException {
        assertTrue(isPublic(AwaitSources.class.getModifiers()));
        assertTrue(isFinal(AwaitSources.class.getModifiers()));
        Constructor<AwaitSources> constructor = AwaitSources.class.getDeclaredConstructor();
        assertTrue(isPrivate(constructor.getModifiers()));
        assertEquals(1, AwaitSources.class.getDeclaredConstructors().length);
    }

    private static List<Class<?>> closedConditionTypes() {
        return List.of(PreservingCondition.class, Present.class, StructuralCondition.class,
                ExplainedCondition.class, ExplainedPreservingCondition.class,
                ExplainedPresent.class, ExplainedStructuralCondition.class);
    }

    private static List<Class<?>> publicTypes() {
        return List.of(AwaitSources.class, Condition.class, Evaluation.class,
                ThrowingConsumer.class, ThrowingFunction.class,
                PreservingCondition.class, Present.class, StructuralCondition.class,
                ExplainedCondition.class, ExplainedPreservingCondition.class,
                ExplainedPresent.class, ExplainedStructuralCondition.class);
    }

    private static List<Class<?>> explainedTypes() {
        return List.of(ExplainedCondition.class, ExplainedPreservingCondition.class,
                ExplainedPresent.class, ExplainedStructuralCondition.class);
    }

    private static void assertNoPublicOrProtectedConstructor(Class<?> type) {
        assertFalse(List.of(type.getDeclaredConstructors()).stream()
                .anyMatch(constructor -> isPublic(constructor.getModifiers())
                        || isProtected(constructor.getModifiers())), type.getName());
    }
}
