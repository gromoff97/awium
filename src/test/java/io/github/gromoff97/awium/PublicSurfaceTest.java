package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exception.*;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    @Test
    void exposesExactlyTheApprovedPublicApiIncludingNestedTypes()
            throws Exception {
        Set<Class<?>> actual = discoveredPublicApiTypes();

        assertEquals(Set.copyOf(approvedPublicApiTypes()), actual);
        Set<Class<?>> topLevel = Set.copyOf(actual.stream()
                .filter(type -> type.getEnclosingClass() == null).toList());
        assertEquals(Set.copyOf(publicTypes()), topLevel);
        for (Class<?> type : topLevel) {
            assertTrue(Set.of("io.github.gromoff97.awium",
                    "io.github.gromoff97.awium.exception")
                    .contains(type.getPackageName()), type.getName());
        }
    }

    @Test
    void publicFailuresLiveInTheirOwnPackage() {
        for (Class<?> type : List.of(AwaitConfigurationConflictException.class,
                AwaitFailure.class, AwaitTimeoutException.class,
                AwaitStabilizationException.class,
                AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class)) {
            assertEquals("io.github.gromoff97.awium.exception",
                    type.getPackageName(), type.getName());
        }
    }

    @Test
    void conditionEvaluationDescriptorsAndExplainedFormsHaveExactShapes()
            throws ReflectiveOperationException {
        assertTrue(isPublic(Condition.class.getModifiers()));
        assertTrue(isAbstract(Condition.class.getModifiers()));
        assertFalse(isFinal(Condition.class.getModifiers()));
        Constructor<?> constructor = Condition.class.getDeclaredConstructor();
        assertTrue(isProtected(constructor.getModifiers()));
        assertTrue(isFinal(Condition.class.getMethod("because", String.class)
                .getModifiers()));
        assertTrue(isFinal(Condition.class.getMethod(
                "because", String.class, Object[].class).getModifiers()));

        assertTrue(isFinal(Evaluation.class.getModifiers()));
        for (Class<?> type : restrictedConstructionTypes()) {
            assertTrue(isPublic(type.getModifiers()), type.getName());
            assertNoPublicOrProtectedConstructor(type);
        }
        for (Class<?> type : closedConditionTypes()) {
            assertTrue(isFinal(type.getModifiers()), type.getName());
        }
        for (Class<?> type : explainedTypes()) {
            assertFalse(Arrays.stream(type.getMethods())
                    .map(Method::getName).anyMatch("because"::equals), type.getName());
        }
    }

    @Test
    void exposesExactlyFiveSourceAndTwoCallbackSams() throws Exception {
        Set<Class<?>> sources = Set.of(AwaitSources.Source.class,
                AwaitSources.OptionalSource.class,
                AwaitSources.CollectionSource.class,
                AwaitSources.SequencedCollectionSource.class,
                AwaitSources.MapSource.class);
        Set<Class<?>> callbacks = Set.of(ThrowingConsumer.class,
                ThrowingFunction.class);

        assertEquals(sources, Set.copyOf(Arrays.asList(
                AwaitSources.class.getDeclaredClasses())));
        assertEquals(callbacks, Set.copyOf(publicTypes().stream()
                .filter(Class::isInterface)
                .filter(type -> !fluentStages().contains(type)).toList()));
        sources.forEach(PublicSurfaceTest::assertCheckedSam);
        callbacks.forEach(PublicSurfaceTest::assertCheckedSam);

        ThrowingConsumer<String> consumer = value -> {
            if (value.isEmpty()) {
                throw new Exception("empty");
            }
        };
        ThrowingFunction<String, Integer> function = String::length;
        AwaitSources.Source<String> source = () -> "source";
        AwaitSources.OptionalSource<String> optionalSource =
                () -> Optional.of("optional");
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
    void allTwentyFluentStagesHaveExactPermittedSubtypeSets() {
        Map<Class<?>, Set<Class<?>>> expected = expectedPermittedSubtypes();

        assertEquals(20, expected.size());
        assertEquals(expected.keySet(), Set.copyOf(fluentStages()));
        expected.forEach((stage, permitted) -> {
            assertTrue(stage.isInterface(), stage.getName());
            assertTrue(stage.isSealed(), stage.getName());
            assertEquals(permitted, Set.copyOf(Arrays.asList(
                    stage.getPermittedSubclasses())), stage.getName());
        });
    }

    @Test
    void namespaceHoldersAndFailureHierarchiesAreClosedExactly()
            throws ReflectiveOperationException {
        for (Class<?> holder : List.of(Awium.class, AwaitConditions.class,
                AwaitSources.class)) {
            assertTrue(isPublic(holder.getModifiers()), holder.getName());
            assertTrue(isFinal(holder.getModifiers()), holder.getName());
            Constructor<?> constructor = holder.getDeclaredConstructor();
            assertTrue(isPrivate(constructor.getModifiers()), holder.getName());
            assertEquals(1, holder.getDeclaredConstructors().length,
                    holder.getName());
            assertTrue(Arrays.stream(holder.getDeclaredMethods())
                    .filter(method -> isPublic(method.getModifiers()))
                    .allMatch(method -> isStatic(method.getModifiers())),
                    holder.getName());
            assertTrue(Arrays.stream(holder.getDeclaredFields())
                    .filter(field -> isPublic(field.getModifiers()))
                    .allMatch(field -> isStatic(field.getModifiers())),
                    holder.getName());

        }

        assertEquals(5, Arrays.stream(Awium.class.getDeclaredMethods())
                .filter(method -> isPublic(method.getModifiers())).count());
        assertEquals(Set.of(AwaitTimeoutException.class,
                        AwaitStabilizationException.class),
                directPublicChildren(AwaitFailure.class));
        assertEquals(Set.of(AwaitSourceRetrievalException.class,
                        AwaitConditionEvaluationException.class,
                        AwaitInterruptedException.class, AwaitUnhandledException.class),
                directPublicChildren(AwaitUncontrolledException.class));
        directPublicChildren(AwaitFailure.class).forEach(type ->
                assertTrue(isFinal(type.getModifiers()), type.getName()));
        directPublicChildren(AwaitUncontrolledException.class).forEach(type ->
                assertTrue(isFinal(type.getModifiers()), type.getName()));
    }

    @Test
    void publicApiContainsNoExcludedSourceTerminalOrContinuationSurface()
            throws Exception {
        assertNoExcludedApiSurface(discoveredPublicApiTypes());
        for (String absent : List.of("AwaitResult", "ExecutableSource",
                "FutureSource", "IterableSource")) {
            try {
                Class.forName("io.github.gromoff97.awium." + absent);
                assertFalse(true, absent);
            } catch (ClassNotFoundException expected) {
                // required absence
            }
        }
    }

    @Test
    void excludedApiAuditRejectsTypeFieldConstructorAndMethodLeaks() {
        for (Class<?> type : List.of(AwaitResult.class,
                ForbiddenFieldName.class, ForbiddenFieldSignature.class,
                ForbiddenConstructorSignature.class, ForbiddenMethodName.class,
                ForbiddenInheritedField.class, ForbiddenInheritedMethod.class,
                ForbiddenForkJoinTaskSignature.class,
                ForbiddenCompletableFutureSignature.class,
                ForbiddenFutureSubtypeSignature.class,
                ForbiddenGenericArraySignature.class,
                ForbiddenLowerWildcardSignature.class,
                ForbiddenIterableSubtypeSignature.class,
                ForbiddenInheritedProtectedSignature.class)) {
            assertThrows(AssertionError.class,
                    () -> assertNoExcludedApiSurface(Set.of(type)),
                    type.getSimpleName());
        }

        assertNoExcludedApiSurface(Set.of(AllowedConcurrencyNames.class));
    }

    private static void assertNoExcludedApiSurface(Collection<Class<?>> types) {
        Set<String> forbiddenNames = Set.of("map", "flatMap", "not", "allOf",
                "anyOf", "execute", "start", "result", "AwaitResult",
                "ExecutableSource", "FutureSource", "IterableSource");

        for (Class<?> type : types) {
            assertFalse(forbiddenNames.contains(type.getSimpleName()),
                    type.toGenericString());
            assertAllowedType(type);
            Arrays.stream(type.getTypeParameters())
                    .forEach(PublicSurfaceTest::assertAllowedType);
            if (type.getGenericSuperclass() != null) {
                assertAllowedType(type.getGenericSuperclass());
            }
            Arrays.stream(type.getGenericInterfaces())
                    .forEach(PublicSurfaceTest::assertAllowedType);
            for (Class<?> inherited : typeHierarchy(type)) {
                for (Field field : inherited.getDeclaredFields()) {
                    if (!isApiMember(field.getModifiers())) {
                        continue;
                    }
                    assertFalse(forbiddenNames.contains(field.getName()),
                            field.toGenericString());
                    assertAllowedType(field.getGenericType());
                }
                for (Method method : inherited.getDeclaredMethods()) {
                    if (!isApiMember(method.getModifiers())) {
                        continue;
                    }
                    assertFalse(forbiddenNames.contains(method.getName()),
                            method.toGenericString());
                    assertAllowedType(method.getGenericReturnType());
                    Arrays.stream(method.getGenericParameterTypes())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                    Arrays.stream(method.getGenericExceptionTypes())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                    Arrays.stream(method.getTypeParameters())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isApiMember(constructor.getModifiers())) {
                    Arrays.stream(constructor.getGenericParameterTypes())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                    Arrays.stream(constructor.getGenericExceptionTypes())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                    Arrays.stream(constructor.getTypeParameters())
                            .forEach(PublicSurfaceTest::assertAllowedType);
                }
            }
        }
    }

    private static boolean isApiMember(int modifiers) {
        return isPublic(modifiers) || isProtected(modifiers);
    }

    private static Set<Class<?>> typeHierarchy(Class<?> type) {
        Set<Class<?>> hierarchy = Collections.newSetFromMap(
                new IdentityHashMap<>());
        List<Class<?>> pending = new ArrayList<>(List.of(type));
        while (!pending.isEmpty()) {
            Class<?> current = pending.removeLast();
            if (!hierarchy.add(current)) {
                continue;
            }
            if (current.getSuperclass() != null) {
                pending.add(current.getSuperclass());
            }
            pending.addAll(Arrays.asList(current.getInterfaces()));
        }
        return hierarchy;
    }

    private static void assertAllowedType(Type type) {
        assertFalse(isForbiddenApiType(type,
                        Collections.newSetFromMap(new IdentityHashMap<>())),
                type.getTypeName());
    }

    private static boolean isForbiddenApiType(Type type, Set<Type> visited) {
        if (!visited.add(type)) {
            return false;
        }
        if (type instanceof Class<?> raw) {
            return raw.isArray()
                    ? isForbiddenApiType(raw.getComponentType(), visited)
                    : isForbiddenApiClass(raw);
        }
        if (type instanceof ParameterizedType parameterized) {
            return isForbiddenApiType(parameterized.getRawType(), visited)
                    || parameterized.getOwnerType() != null
                    && isForbiddenApiType(parameterized.getOwnerType(), visited)
                    || Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(argument -> isForbiddenApiType(argument, visited));
        }
        if (type instanceof GenericArrayType array) {
            return isForbiddenApiType(array.getGenericComponentType(), visited);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited));
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited))
                    || Arrays.stream(wildcard.getLowerBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited));
        }
        throw new AssertionError("unsupported reflection type: " + type);
    }

    private static boolean isForbiddenApiClass(Class<?> type) {
        if (Iterable.class.isAssignableFrom(type)
                && !Collection.class.isAssignableFrom(type)) {
            return true;
        }
        if (List.of(Future.class, Callable.class, Runnable.class, Predicate.class)
                .stream().anyMatch(forbidden -> forbidden.isAssignableFrom(type))) {
            return true;
        }
        String packageName = type.getPackageName();
        return packageName.equals("org.assertj")
                || packageName.startsWith("org.assertj.")
                || packageName.equals("org.awaitility")
                || packageName.startsWith("org.awaitility.");
    }

    private static void assertCheckedSam(Class<?> type) {
        assertTrue(type.isInterface(), type.getName());
        assertTrue(type.isAnnotationPresent(FunctionalInterface.class), type.getName());
        List<Method> abstractMethods = Arrays.stream(type.getMethods())
                .filter(method -> isAbstract(method.getModifiers()))
                .toList();
        assertEquals(1, abstractMethods.size(), type.getName());
        assertEquals(1, abstractMethods.getFirst().getExceptionTypes().length,
                type.getName());
        assertEquals(Exception.class,
                abstractMethods.getFirst().getExceptionTypes()[0], type.getName());
    }

    private static Set<Class<?>> directPublicChildren(Class<?> parent) {
        return Set.copyOf(publicTypes().stream()
                .filter(type -> type.getSuperclass() == parent).toList());
    }

    private static Set<Class<?>> approvedPublicApiTypes() {
        Set<Class<?>> types = new java.util.HashSet<>(publicTypes());
        types.addAll(fluentStages());
        types.addAll(List.of(AwaitSources.Source.class,
                AwaitSources.OptionalSource.class,
                AwaitSources.CollectionSource.class,
                AwaitSources.SequencedCollectionSource.class,
                AwaitSources.MapSource.class));
        return types;
    }

    private static Set<Class<?>> discoveredPublicApiTypes() throws Exception {
        Path classes = Path.of("build", "classes", "java", "main");
        Set<Class<?>> types = new java.util.HashSet<>();
        try (var entries = Files.walk(classes)) {
            for (Path entry : entries.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted().toList()) {
                String binaryName = classes.relativize(entry).toString()
                        .replace(entry.getFileSystem().getSeparator(), ".");
                binaryName = binaryName.substring(
                        0, binaryName.length() - ".class".length());
                if (binaryName.equals("module-info")) {
                    continue;
                }
                Class<?> type = Class.forName(binaryName, false,
                        PublicSurfaceTest.class.getClassLoader());
                if (isPublicApiType(type)) {
                    types.add(type);
                }
            }
        }
        return Set.copyOf(types);
    }

    private static boolean isPublicApiType(Class<?> type) {
        if (type.getPackageName().startsWith(
                "io.github.gromoff97.awium.internal.")) {
            return false;
        }
        for (Class<?> current = type; current != null;
                current = current.getEnclosingClass()) {
            if (!isPublic(current.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static Map<Class<?>, Set<Class<?>>> expectedPermittedSubtypes() {
        return Map.ofEntries(
                Map.entry(ObjectAwait.class,
                        Set.of(ObjectStageAdapters.ObjectInitialStage.class)),
                Map.entry(ObjectAwait.AfterEvery.class,
                        Set.of(ObjectStageAdapters.ObjectAfterEveryStage.class)),
                Map.entry(ObjectAwait.AfterUpTo.class,
                        Set.of(ObjectAwait.AfterEvery.class,
                                ObjectStageAdapters.ObjectAfterUpToStage.class)),
                Map.entry(ObjectUntil.class,
                        Set.of(ObjectAwait.class, ObjectAwait.AfterUpTo.class,
                                OptionalUntil.class, CollectionUntil.class,
                                MapUntil.class,
                                ObjectStageAdapters.ObjectTerminalStage.class)),
                Map.entry(OptionalAwait.class,
                        Set.of(OptionalStageAdapters.OptionalInitialStage.class)),
                Map.entry(OptionalAwait.AfterEvery.class,
                        Set.of(OptionalStageAdapters.OptionalAfterEveryStage.class)),
                Map.entry(OptionalAwait.AfterUpTo.class,
                        Set.of(OptionalAwait.AfterEvery.class,
                                OptionalStageAdapters.OptionalAfterUpToStage.class)),
                Map.entry(OptionalUntil.class,
                        Set.of(OptionalAwait.class, OptionalAwait.AfterUpTo.class,
                                OptionalStageAdapters.OptionalTerminalStage.class)),
                Map.entry(CollectionAwait.class,
                        Set.of(CollectionStageAdapters.CollectionInitialStage.class)),
                Map.entry(CollectionAwait.AfterEvery.class,
                        Set.of(CollectionStageAdapters.CollectionAfterEveryStage.class)),
                Map.entry(CollectionAwait.AfterUpTo.class,
                        Set.of(CollectionAwait.AfterEvery.class,
                                CollectionStageAdapters.CollectionAfterUpToStage.class)),
                Map.entry(CollectionUntil.class,
                        Set.of(CollectionAwait.class,
                                CollectionAwait.AfterUpTo.class,
                                SequencedCollectionUntil.class,
                                CollectionStageAdapters.CollectionTerminalStage.class)),
                Map.entry(SequencedCollectionAwait.class,
                        Set.of(SequencedCollectionStageAdapters
                                .SequencedCollectionInitialStage.class)),
                Map.entry(SequencedCollectionAwait.AfterEvery.class,
                        Set.of(SequencedCollectionStageAdapters
                                .SequencedCollectionAfterEveryStage.class)),
                Map.entry(SequencedCollectionAwait.AfterUpTo.class,
                        Set.of(SequencedCollectionAwait.AfterEvery.class,
                                SequencedCollectionStageAdapters
                                        .SequencedCollectionAfterUpToStage.class)),
                Map.entry(SequencedCollectionUntil.class,
                        Set.of(SequencedCollectionAwait.class,
                                SequencedCollectionAwait.AfterUpTo.class,
                                SequencedCollectionStageAdapters
                                        .SequencedCollectionTerminalStage.class)),
                Map.entry(MapAwait.class,
                        Set.of(MapStageAdapters.MapInitialStage.class)),
                Map.entry(MapAwait.AfterEvery.class,
                        Set.of(MapStageAdapters.MapAfterEveryStage.class)),
                Map.entry(MapAwait.AfterUpTo.class,
                        Set.of(MapAwait.AfterEvery.class,
                                MapStageAdapters.MapAfterUpToStage.class)),
                Map.entry(MapUntil.class,
                        Set.of(MapAwait.class, MapAwait.AfterUpTo.class,
                                MapStageAdapters.MapTerminalStage.class)));
    }

    private static List<Class<?>> fluentStages() {
        return List.of(ObjectAwait.class, ObjectAwait.AfterEvery.class,
                ObjectAwait.AfterUpTo.class, ObjectUntil.class,
                OptionalAwait.class, OptionalAwait.AfterEvery.class,
                OptionalAwait.AfterUpTo.class, OptionalUntil.class,
                CollectionAwait.class, CollectionAwait.AfterEvery.class,
                CollectionAwait.AfterUpTo.class, CollectionUntil.class,
                SequencedCollectionAwait.class,
                SequencedCollectionAwait.AfterEvery.class,
                SequencedCollectionAwait.AfterUpTo.class,
                SequencedCollectionUntil.class, MapAwait.class,
                MapAwait.AfterEvery.class, MapAwait.AfterUpTo.class,
                MapUntil.class);
    }

    private static List<Class<?>> closedConditionTypes() {
        return List.of(PreservingCondition.class, Present.class,
                StructuralCondition.class, ExplainedCondition.class,
                ExplainedPreservingCondition.class, ExplainedPresent.class,
                ExplainedStructuralCondition.class);
    }

    private static List<Class<?>> restrictedConstructionTypes() {
        List<Class<?>> types = new ArrayList<>(closedConditionTypes());
        types.add(Evaluation.class);
        types.addAll(List.of(AwaitFailure.class,
                AwaitUncontrolledException.class));
        return types;
    }

    private static List<Class<?>> publicTypes() {
        return List.of(Awium.class, AwaitConditions.class,
                AwaitSources.class, Condition.class, Evaluation.class,
                ThrowingConsumer.class, ThrowingFunction.class,
                PreservingCondition.class, Present.class, StructuralCondition.class,
                ExplainedCondition.class, ExplainedPreservingCondition.class,
                ExplainedPresent.class, ExplainedStructuralCondition.class,
                ObjectAwait.class, ObjectUntil.class, OptionalAwait.class,
                OptionalUntil.class, CollectionAwait.class, CollectionUntil.class,
                SequencedCollectionAwait.class, SequencedCollectionUntil.class,
                MapAwait.class, MapUntil.class,
                AwaitConfigurationConflictException.class, AwaitFailure.class,
                AwaitTimeoutException.class, AwaitStabilizationException.class,
                AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class);
    }

    private static List<Class<?>> explainedTypes() {
        return List.of(ExplainedCondition.class, ExplainedPreservingCondition.class,
                ExplainedPresent.class, ExplainedStructuralCondition.class);
    }

    private static void assertNoPublicOrProtectedConstructor(Class<?> type) {
        assertFalse(Arrays.stream(type.getDeclaredConstructors())
                .anyMatch(constructor -> isPublic(constructor.getModifiers())
                        || isProtected(constructor.getModifiers())), type.getName());
    }

    public static final class AwaitResult {
    }

    public static final class ForbiddenFieldName {
        public Object result;
    }

    public static final class ForbiddenFieldSignature {
        public java.util.concurrent.Future<?> leaked;
    }

    public static final class ForbiddenConstructorSignature {
        public ForbiddenConstructorSignature(
                java.util.function.Predicate<?> predicate) {
        }
    }

    public static final class ForbiddenMethodName {
        public Object map() {
            return null;
        }
    }

    static class ForbiddenInheritedFieldParent {
        public java.util.concurrent.Future<?> leaked;
    }

    public static final class ForbiddenInheritedField
            extends ForbiddenInheritedFieldParent {
    }

    interface ForbiddenInheritedMethodParent {
        default java.util.function.Predicate<?> map() {
            return null;
        }
    }

    public static final class ForbiddenInheritedMethod
            implements ForbiddenInheritedMethodParent {
    }

    public static final class ForbiddenForkJoinTaskSignature {
        public List<? extends java.util.concurrent.ForkJoinTask<?>[]> leaked;
    }

    public static final class ForbiddenCompletableFutureSignature {
        public java.util.concurrent.CompletableFuture<?> leaked;
    }

    static final class FutureSubtype<T>
            extends java.util.concurrent.CompletableFuture<T> {
    }

    public static final class ForbiddenFutureSubtypeSignature {
        public FutureSubtype<?> leaked;
    }

    public static final class ForbiddenGenericArraySignature<
            T extends java.util.concurrent.Future<?>> {
        public T[] leaked;
    }

    public static final class ForbiddenLowerWildcardSignature {
        public List<? super java.util.concurrent.ForkJoinTask<?>> leaked;
    }

    static final class IterableSubtype implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return List.<String>of().iterator();
        }
    }

    public static final class ForbiddenIterableSubtypeSignature {
        public IterableSubtype leaked;
    }

    static class ForbiddenInheritedProtectedParent {
        protected java.util.concurrent.Future<?> leaked;
    }

    public static final class ForbiddenInheritedProtectedSignature
            extends ForbiddenInheritedProtectedParent {
    }

    public static final class AllowedConcurrencyNames {
        public FutureProof future;

        public PredicateResult value() {
            return null;
        }
    }

    public static final class FutureProof {
    }

    public static final class PredicateResult {
    }

}
