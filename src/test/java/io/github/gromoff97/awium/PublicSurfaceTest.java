package io.github.gromoff97.awium;


import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.*;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.await.stages.OptionalAwaitStage;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.nio.file.Files.walk;
import static java.util.Collections.newSetFromMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    private static final Set<String> PUBLIC_API_PACKAGES = Set.of(
            "io.github.gromoff97.awium",
            "io.github.gromoff97.awium.await",
            "io.github.gromoff97.awium.sources",
            "io.github.gromoff97.awium.conditioning",
            "io.github.gromoff97.awium.conditioning.conditions",
            "io.github.gromoff97.awium.conditioning.providers",
            "io.github.gromoff97.awium.exceptions");

    @Test
    void exposesExactlyTheApprovedPublicApiIncludingNestedTypes()
            throws Exception {
        Set<Class<?>> actual = discoveredPublicApiTypes();

        assertEquals(Set.copyOf(approvedPublicApiTypes()), actual);
        Set<Class<?>> topLevel = Set.copyOf(actual.stream()
                .filter(type -> type.getEnclosingClass() == null).toList());
        assertEquals(Set.copyOf(publicTypes()), topLevel);
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
    void exposesExactlyFourSourceAndTwoCallbackSams() throws Exception {
        Set<Class<?>> sources = Set.of(Source.class, OptionalSource.class,
                CollectionSource.class, MapSource.class);
        Set<Class<?>> callbacks = Set.of(CheckedConsumer.class,
                CheckedFunction.class);

        assertEquals(callbacks, Set.copyOf(publicTypes().stream()
                .filter(Class::isInterface)
                .filter(type -> !sources.contains(type))
                .filter(type -> !fluentStages().contains(type)).toList()));
        sources.forEach(PublicSurfaceTest::assertCheckedSam);
        callbacks.forEach(PublicSurfaceTest::assertCheckedSam);

        CheckedConsumer<String> consumer = value -> {
            if (value.isEmpty()) {
                throw new Exception("empty");
            }
        };
        CheckedFunction<String, Integer> function = String::length;
        Source<String> source = () -> "source";
        OptionalSource<String> optionalSource =
                () -> Optional.of("optional");
        CollectionSource<Collection<String>> collectionSource =
                () -> List.of("collection");
        MapSource<Map<String, Integer>> mapSource =
                () -> Map.of("map", 1);

        consumer.accept("value");
        assertEquals(5, function.apply("value"));
        assertEquals("source", source.get());
        assertEquals("optional", optionalSource.get().orElseThrow());
        assertEquals("collection", collectionSource.get().iterator().next());
        assertEquals(1, mapSource.get().get("map"));
    }

    @Test
    void allThreeFluentInterfacesHaveExactPermittedSubtypeSets() {
        Map<Class<?>, Set<Class<?>>> expected = Map.of(
                Await.class, Set.of(
                        AwaitStage.class, OptionalAwait.class,
                        StructuralAwait.class),
                OptionalAwait.class, Set.of(OptionalAwaitStage.class),
                StructuralAwait.class, Set.of(StructuralAwaitStage.class));

        assertEquals(expected.keySet(), Set.copyOf(fluentStages()));
        expected.forEach((stage, permitted) -> {
            assertTrue(stage.isInterface(), stage.getName());
            assertTrue(stage.isSealed(), stage.getName());
            assertEquals(permitted, Set.copyOf(Arrays.asList(
                    stage.getPermittedSubclasses())), stage.getName());
            permitted.forEach(type -> assertTrue(type.isSealed()
                    || isFinal(type.getModifiers()),
                    () -> stage + " permits open subtype " + type));
        });
    }

    @Test
    void namespaceHoldersAndFailureHierarchiesAreClosedExactly()
            throws ReflectiveOperationException {
        for (Class<?> holder : List.of(Awium.class, ConditionProvider.class,
                ObjectConditionProvider.class, OptionalConditionProvider.class,
                CollectionConditionProvider.class, MapConditionProvider.class)) {
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

        assertEquals(4, Arrays.stream(Awium.class.getDeclaredMethods())
                .filter(method -> isPublic(method.getModifiers())).count());
        assertEquals(Set.of("condition", "asserted", "passed"),
                Set.copyOf(Arrays.stream(ConditionProvider.class
                                .getDeclaredMethods())
                        .filter(method -> isPublic(method.getModifiers()))
                        .map(Method::getName).toList()));
        assertFalse(Arrays.stream(ConditionProvider.class.getDeclaredFields())
                .anyMatch(field -> isPublic(field.getModifiers())));
        assertEquals(Set.of(AwaitTimeoutException.class,
                        AwaitStabilizationException.class),
                directPublicChildren(AwaitFailure.class));
        assertEquals(Set.of(AwaitSourceRetrievalException.class,
                        AwaitConditionEvaluationException.class,
                        AwaitInterruptedException.class, AwaitUnhandledException.class),
                directPublicChildren(AwaitUncontrolledException.class));
    }

    @Test
    void publicApiContainsNoExcludedSourceTerminalOrContinuationSurface()
            throws Exception {
        assertNoExcludedApiSurface(discoveredPublicApiTypes());
        for (String absent : List.of("AwaitResult", "ExecutableSource",
                "FutureSource", "IterableSource")) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("io.github.gromoff97.awium." + absent),
                    absent);
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
                    assertFalse(forbiddenNames.contains(method.getName())
                                    && !(inherited == Evaluation.class
                                    && method.getName().equals("result")),
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
        Set<Class<?>> hierarchy = newSetFromMap(
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
                        newSetFromMap(new IdentityHashMap<>())),
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
        types.addAll(explainedTypes());
        types.add(Evaluation.Status.class);
        return types;
    }

    private static Set<Class<?>> discoveredPublicApiTypes() throws Exception {
        Path classes = Path.of("build", "classes", "java", "main");
        Set<Class<?>> types = new java.util.HashSet<>();
        try (var entries = walk(classes)) {
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
        if (!PUBLIC_API_PACKAGES.contains(type.getPackageName())) {
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

    private static List<Class<?>> fluentStages() {
        return List.of(Await.class, OptionalAwait.class, StructuralAwait.class);
    }

    private static List<Class<?>> closedConditionTypes() {
        return List.of(PreservingCondition.class, PresentCondition.class,
                StructuralCondition.class, Condition.ExplainedCondition.class,
                PreservingCondition.ExplainedCondition.class, PresentCondition.ExplainedCondition.class,
                StructuralCondition.ExplainedCondition.class);
    }

    private static List<Class<?>> restrictedConstructionTypes() {
        List<Class<?>> types = new ArrayList<>(closedConditionTypes());
        types.add(Evaluation.class);
        types.addAll(List.of(AwaitFailure.class,
                AwaitUncontrolledException.class));
        return types;
    }

    private static List<Class<?>> publicTypes() {
        return List.of(Awium.class, ConditionProvider.class,
                ObjectConditionProvider.class, OptionalConditionProvider.class,
                CollectionConditionProvider.class, MapConditionProvider.class,
                Source.class, OptionalSource.class, CollectionSource.class,
                MapSource.class, Condition.class, Evaluation.class,
                ValueEquality.class, RuntimeCondition.class,
                CheckedConsumer.class, CheckedFunction.class,
                PreservingCondition.class, PresentCondition.class, StructuralCondition.class,
                Await.class, OptionalAwait.class, StructuralAwait.class,
                AwaitConfigurationConflictException.class, AwaitFailure.class,
                AwaitTimeoutException.class, AwaitStabilizationException.class,
                AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class);
    }

    private static List<Class<?>> explainedTypes() {
        return List.of(Condition.ExplainedCondition.class, PreservingCondition.ExplainedCondition.class,
                PresentCondition.ExplainedCondition.class, StructuralCondition.ExplainedCondition.class);
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

    public static final class ForbiddenInheritedField extends ForbiddenInheritedFieldParent {
    }

    interface ForbiddenInheritedMethodParent {
        default java.util.function.Predicate<?> map() {
            return null;
        }
    }

    public static final class ForbiddenInheritedMethod implements ForbiddenInheritedMethodParent {
    }

    public static final class ForbiddenForkJoinTaskSignature {
        public List<? extends java.util.concurrent.ForkJoinTask<?>[]> leaked;
    }

    public static final class ForbiddenCompletableFutureSignature {
        public java.util.concurrent.CompletableFuture<?> leaked;
    }

    public static final class ForbiddenGenericArraySignature<
            T extends java.util.concurrent.Future<?>> {
        public T[] leaked;
    }

    public static final class ForbiddenLowerWildcardSignature {
        public List<? super java.util.concurrent.ForkJoinTask<?>> leaked;
    }

    public static final class ForbiddenIterableSubtypeSignature {
        public Path leaked;
    }

    static class ForbiddenInheritedProtectedParent {
        protected java.util.concurrent.Future<?> leaked;
    }

    public static final class ForbiddenInheritedProtectedSignature extends ForbiddenInheritedProtectedParent {
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
