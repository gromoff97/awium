package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.await.AwaitResult;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.NarrowingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.Conditions;
import io.github.gromoff97.awium.sources.Source;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.nio.file.Files.walk;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleFinder;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PublicSurfaceTest {

    private static final Set<String> PUBLIC_API_PACKAGES = Set.of(
            "io.github.gromoff97.awium.await",
            "io.github.gromoff97.awium.sources",
            "io.github.gromoff97.awium.conditioning",
            "io.github.gromoff97.awium.conditioning.conditions",
            "io.github.gromoff97.awium.exceptions");

    @Test
    void moduleExportsOnlyThePublicApiPackages() {
        Set<String> exports = ModuleFinder.of(ArtifactContractIT.JAR)
                .find("io.github.gromoff97.awium").orElseThrow()
                .descriptor().exports().stream()
                .map(export -> export.source()).collect(java.util.stream.Collectors.toSet());
        assertEquals(PUBLIC_API_PACKAGES, exports);
    }

    @Test
    void publicApiDoesNotLeakExcludedSurfaceAndKeepsSourceChecked() throws Exception {
        Set<Class<?>> types = discoveredPublicApiTypes();
        assertNoExcludedApiSurface(types);
        for (Class<?> type : types) {
            if (Source.class.isAssignableFrom(type)) {
                assertCheckedSam(type);
            }
        }
    }

    @Test
    void excludedSurfaceAuditCoversNamesAndNestedSignatures() {
        for (Class<?> type : List.of(ForbiddenFieldSignature.class,
                ForbiddenMethodName.class,
                ForbiddenInheritedField.class,
                ForbiddenGenericSignature.class,
                ForbiddenIterableSubtypeSignature.class,
                ForbiddenCheckedSignature.class)) {
            assertThrows(AssertionError.class,
                    () -> assertNoExcludedApiSurface(Set.of(type)),
                    type.getSimpleName());
        }
        assertNoExcludedApiSurface(Set.of(AllowedConcurrencyNames.class,
                AllowedJdkFunctionalSignature.class));
    }

    @Test
    void publicConditionStagesDoNotExposeRuntimeMechanicsOrFictitiousResults() {
        Set<String> runtimeMethods = Set.of("description", "explanation", "evaluatorFactory", "newEvaluator");
        for (Class<?> stage : List.of(ConditionStage.class, ResultStage.class, PreservingStage.class,
                ExpectedStage.class, ExpectedSequenceStage.class, NarrowingStage.class,
                SelectedStage.class, SelectedSequenceStage.class)) {
            stream(stage.getMethods()).forEach(method -> assertFalse(runtimeMethods.contains(method.getName()),
                    method.toGenericString()));
        }
        assertFalse(ConditionStage.class.isAssignableFrom(SelectedStage.class));
        assertFalse(ConditionStage.class.isAssignableFrom(SelectedSequenceStage.class));
    }

    @Test
    void expectedValueFactoriesExposeGenericOperandsInsteadOfObject() {
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

    private static void assertNoExcludedApiSurface(Collection<Class<?>> types) {
        Set<String> forbiddenNames = Set.of("map", "flatMap", "not", "allOf",
                "anyOf", "execute", "start", "result", "caught", "tryAwait",
                "singleKey", "singleValue", "extracting",
                "ObjectCondition", "ComparableCondition", "CollectionCondition",
                "MapCondition", "OptionalCondition", "StringCondition",
                "ExecutableSource", "FutureSource", "IterableSource");

        for (Class<?> type : types) {
            assertFalse(forbiddenNames.contains(type.getSimpleName()),
                    type.toGenericString());
            assertAllowedType(type);
            stream(type.getTypeParameters()).forEach(
                    PublicSurfaceTest::assertAllowedType);
            if (type.getGenericSuperclass() != null) {
                assertAllowedType(type.getGenericSuperclass());
            }
            stream(type.getGenericInterfaces()).forEach(
                    PublicSurfaceTest::assertAllowedType);
            for (Class<?> inherited : typeHierarchy(type)) {
                for (Field field : inherited.getDeclaredFields()) {
                    if (isApiMember(field.getModifiers())) {
                        assertFalse(forbiddenNames.contains(field.getName()),
                                field.toGenericString());
                        assertAllowedType(field.getGenericType());
                    }
                }
                for (Method method : inherited.getDeclaredMethods()) {
                    if (!isApiMember(method.getModifiers())) {
                        continue;
                    }
                    assertFalse(forbiddenNames.contains(method.getName())
                                    && !explicitlyAllowedForbiddenMethod(inherited, method),
                            method.toGenericString());
                    assertAllowedType(method.getGenericReturnType());
                    stream(method.getGenericParameterTypes()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                    stream(method.getGenericExceptionTypes()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                    stream(method.getTypeParameters()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isApiMember(constructor.getModifiers())) {
                    stream(constructor.getGenericParameterTypes()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                    stream(constructor.getGenericExceptionTypes()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                    stream(constructor.getTypeParameters()).forEach(
                            PublicSurfaceTest::assertAllowedType);
                }
            }
        }
    }

    private static boolean isApiMember(int modifiers) {
        return isPublic(modifiers) || isProtected(modifiers);
    }

    private static boolean explicitlyAllowedForbiddenMethod(Class<?> owner, Method method) {
        return method.getName().equals("tryAwait") && owner == Await.class
                || method.getName().equals("result")
                && (owner == Evaluation.class
                || owner == AwaitResult.Satisfied.class
                || owner == AwaitAttempt.Outcome.Satisfied.class);
    }

    private static Set<Class<?>> typeHierarchy(Class<?> type) {
        Set<Class<?>> hierarchy = new HashSet<>();
        List<Class<?>> pending = new ArrayList<>(List.of(type));
        while (!pending.isEmpty()) {
            Class<?> current = pending.removeLast();
            if (hierarchy.add(current)) {
                if (current.getSuperclass() != null) {
                    pending.add(current.getSuperclass());
                }
                pending.addAll(List.of(current.getInterfaces()));
            }
        }
        return hierarchy;
    }

    private static void assertAllowedType(Type type) {
        assertFalse(isForbiddenApiType(type, new HashSet<>()),
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
                    || stream(parameterized.getActualTypeArguments())
                    .anyMatch(argument -> isForbiddenApiType(argument, visited));
        }
        if (type instanceof GenericArrayType array) {
            return isForbiddenApiType(array.getGenericComponentType(), visited);
        }
        if (type instanceof TypeVariable<?> variable) {
            return stream(variable.getBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited));
        }
        if (type instanceof WildcardType wildcard) {
            return stream(wildcard.getUpperBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited))
                    || stream(wildcard.getLowerBounds())
                    .anyMatch(bound -> isForbiddenApiType(bound, visited));
        }
        throw new AssertionError("unsupported reflection type: " + type);
    }

    private static boolean isForbiddenApiClass(Class<?> type) {
        String packageName = type.getPackageName();
        return type.getSimpleName().startsWith("Checked")
                || type.getSimpleName().endsWith("Evaluator")
                || type.getSimpleName().endsWith("Session")
                || packageName.startsWith("io.github.gromoff97.awium")
                && !PUBLIC_API_PACKAGES.contains(packageName)
                && type.getNestHost() != PublicSurfaceTest.class
                || Iterable.class.isAssignableFrom(type)
                && !Collection.class.isAssignableFrom(type)
                || Future.class.isAssignableFrom(type)
                || Callable.class.isAssignableFrom(type)
                || Runnable.class.isAssignableFrom(type)
                || packageName.equals("org.assertj")
                || packageName.startsWith("org.assertj.")
                || packageName.equals("org.awaitility")
                || packageName.startsWith("org.awaitility.");
    }

    private static void assertCheckedSam(Class<?> type) {
        List<Method> abstractMethods = stream(type.getMethods())
                .filter(method -> isAbstract(method.getModifiers()))
                .toList();
        assertEquals(1, abstractMethods.size(), type.getName());
        assertEquals(List.of(Exception.class),
                List.of(abstractMethods.getFirst().getExceptionTypes()), type.getName());
    }

    private static Set<Class<?>> discoveredPublicApiTypes() throws Exception {
        Path classes = Path.of("build", "classes", "java", "main");
        Set<Class<?>> types = new HashSet<>();
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
        return types;
    }

    private static boolean isPublicApiType(Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        return PUBLIC_API_PACKAGES.contains(type.getPackageName())
                && isPublic(type.getModifiers())
                && (enclosing == null || isPublicApiType(enclosing));
    }

    public static final class ForbiddenFieldSignature {
        public Future<?> leaked;
    }

    public static final class ForbiddenMethodName {
        public void map() {}
    }

    static class ForbiddenInheritedFieldParent {
        public Future<?> leaked;
    }

    public static final class ForbiddenInheritedField extends ForbiddenInheritedFieldParent {}

    public static final class ForbiddenGenericSignature<
            T extends Future<?>> {
        public List<? super T[]> leaked;
    }

    public static final class ForbiddenIterableSubtypeSignature {
        public Path leaked;
    }

    public static final class ForbiddenCheckedSignature {
        public CheckedEvaluator leaked;
    }

    public static final class CheckedEvaluator {}

    public static final class AllowedConcurrencyNames {
        public FutureProof future;

        public PredicateResult value() { return null; }
    }

    public static final class AllowedJdkFunctionalSignature {
        public void callbacks(Function<String, String> function,
                Predicate<String> predicate, BiPredicate<String, Integer> biPredicate,
                Consumer<String> consumer) {}
    }

    public static final class FutureProof {}

    public static final class PredicateResult {}
}
