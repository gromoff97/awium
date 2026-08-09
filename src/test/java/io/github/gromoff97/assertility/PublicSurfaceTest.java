package io.github.gromoff97.assertility;

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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicSurfaceTest {

    private static final Path MAIN_PACKAGE = Path.of("src", "main", "java", "io",
            "github", "gromoff97", "assertility");

    @Test
    void exposesExactlyTheApprovedPublicApiIncludingNestedTypes()
            throws Exception {
        Set<Class<?>> actual = discoveredPublicApiTypes();

        assertEquals(Set.copyOf(approvedPublicApiTypes()), actual);
        Set<Class<?>> topLevel = Set.copyOf(actual.stream()
                .filter(type -> type.getEnclosingClass() == null).toList());
        assertEquals(Set.copyOf(publicTypes()), topLevel);
        for (Class<?> type : topLevel) {
            assertEquals("io.github.gromoff97.assertility", type.getPackageName());
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
            throws ReflectiveOperationException, IOException {
        for (Class<?> holder : List.of(Assertility.class, AwaitConditions.class,
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

            String source = Files.readString(MAIN_PACKAGE.resolve(
                    holder.getSimpleName() + ".java"));
            Pattern emptyConstructor = Pattern.compile("private\\s+"
                    + Pattern.quote(holder.getSimpleName())
                    + "\\s*\\(\\s*\\)\\s*\\{\\s*}");
            assertEquals(1, emptyConstructor.matcher(source).results().count(),
                    holder.getName());
        }

        assertEquals(5, Arrays.stream(Assertility.class.getDeclaredMethods())
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
                Class.forName("io.github.gromoff97.assertility." + absent);
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
                ForbiddenConstructorSignature.class, ForbiddenMethodName.class)) {
            assertThrows(AssertionError.class,
                    () -> assertNoExcludedApiSurface(Set.of(type)),
                    type.getSimpleName());
        }
    }

    private static void assertNoExcludedApiSurface(Collection<Class<?>> types) {
        Set<String> forbiddenNames = Set.of("map", "flatMap", "not", "allOf",
                "anyOf", "execute", "start", "result", "AwaitResult",
                "ExecutableSource", "FutureSource", "IterableSource");
        List<String> forbiddenTypes = List.of("java.util.concurrent.Future",
                "java.util.concurrent.Callable", "java.lang.Runnable",
                "java.lang.Iterable", "java.util.function.Predicate",
                "org.assertj", "org.awaitility");

        for (Class<?> type : types) {
            assertFalse(forbiddenNames.contains(type.getSimpleName()),
                    type.toGenericString());
            assertAllowedSignature(type.toGenericString(), forbiddenTypes);
            if (type.getGenericSuperclass() != null) {
                assertAllowedSignature(type.getGenericSuperclass().getTypeName(),
                        forbiddenTypes);
            }
            Arrays.stream(type.getGenericInterfaces()).forEach(parent ->
                    assertAllowedSignature(parent.getTypeName(), forbiddenTypes));
            for (Field field : type.getDeclaredFields()) {
                if (isApiMember(field.getModifiers())) {
                    assertFalse(forbiddenNames.contains(field.getName()),
                            field.toGenericString());
                    assertAllowedSignature(field.toGenericString(), forbiddenTypes);
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isApiMember(constructor.getModifiers())) {
                    assertAllowedSignature(constructor.toGenericString(),
                            forbiddenTypes);
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (isApiMember(method.getModifiers())) {
                    assertFalse(forbiddenNames.contains(method.getName()),
                            method.toGenericString());
                    assertAllowedSignature(method.toGenericString(), forbiddenTypes);
                }
            }
        }
    }

    private static boolean isApiMember(int modifiers) {
        return isPublic(modifiers) || isProtected(modifiers);
    }

    private static void assertAllowedSignature(
            String signature, Collection<String> forbiddenTypes) {
        forbiddenTypes.forEach(forbidden -> assertFalse(
                signature.contains(forbidden), signature));
    }

    @Test
    void productionSourcesUseOnlyTheApprovedWaitingAndInterruptionMechanics()
            throws IOException {
        assertApprovedProductionSources(productionSources(MAIN_PACKAGE));
    }

    @Test
    void productionSourceAuditRecursesIntoSubpackages(@TempDir Path root)
            throws IOException {
        Path nested = root.resolve("worker").resolve("Mutant.java");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, """
                final class Mutant extends Thread {
                }
                """);

        assertThrows(AssertionError.class,
                () -> assertApprovedProductionSources(productionSources(root)));
    }

    @Test
    void productionSourceAuditRejectsEveryApprovedBan() {
        Map<String, String> mutants = Map.ofEntries(
                Map.entry("thread subclass", "class Mutant extends Thread {}"),
                Map.entry("worker start",
                        "class Mutant { void run(Thread worker) { worker.start(); } }"),
                Map.entry("interrupt read reference",
                        "class Mutant { java.util.function.BooleanSupplier read = Thread::interrupted; }"),
                Map.entry("interrupt restore reference",
                        "class Mutant { Object restore(Thread t) { return t::interrupt; } }"),
                Map.entry("sleep",
                        "class Mutant { void run() throws Exception { Thread.sleep(1); } }"),
                Map.entry("executor", "class Mutant { Executor worker; }"),
                Map.entry("future", "class Mutant { CompletableFuture<?> future; }"),
                Map.entry("thread construction",
                        "class Mutant { Thread worker = new Thread(); }"),
                Map.entry("thread builder",
                        "class Mutant { Object builder = Thread.ofVirtual(); }"),
                Map.entry("monitor method",
                        "class Mutant { void run(Object lock) throws Exception { lock.wait(); } }"),
                Map.entry("synchronized monitor",
                        "class Mutant { synchronized void run() {} }"),
                Map.entry("atomic", "class Mutant { AtomicInteger state; }"),
                Map.entry("scheduler", "class Mutant { Timer timer; }"),
                Map.entry("schedule call",
                        "class Mutant { void run(Object scheduler) { scheduler.schedule(); } }"),
                Map.entry("lock", "class Mutant { ReentrantLock lock; }"),
                Map.entry("LockSupport outside JdkTime",
                        "class Mutant { void run() { LockSupport.park(); } }"),
                Map.entry("interrupt read",
                        "class Mutant { boolean read(Thread t) { return t.isInterrupted(); } }"),
                Map.entry("interrupt restore",
                        "class Mutant { void restore(Thread t) { t.interrupt(); } }"));

        mutants.forEach((name, source) -> assertThrows(AssertionError.class,
                () -> assertApprovedProductionSources(
                        Map.of(Path.of(name + ".java"), source)), name));
    }

    private static void assertApprovedProductionSources(
            Map<Path, String> sources) {
        List<Pattern> forbidden = List.of(
                Pattern.compile("\\bThread\\s*\\.\\s*sleep\\s*\\("),
                Pattern.compile("\\b(?:Executor|ExecutorService|Executors|"
                        + "CompletableFuture|CompletionStage|Future|FutureTask|"
                        + "ForkJoinPool|ThreadFactory)\\b"),
                Pattern.compile("new\\s+(?:java\\s*\\.\\s*lang\\s*\\.\\s*)?"
                        + "Thread\\s*\\("),
                Pattern.compile("\\bThread\\s*\\.\\s*(?:ofVirtual|ofPlatform|"
                        + "startVirtualThread)\\s*\\("),
                Pattern.compile("\\bextends\\s+(?:java\\s*\\.\\s*lang"
                        + "\\s*\\.\\s*)?Thread\\b"),
                Pattern.compile("(?:\\.|::)\\s*start\\s*(?:\\(|\\b)"),
                Pattern.compile("\\bsynchronized\\b"),
                Pattern.compile("(?:\\.|::)\\s*(?:wait|notify|notifyAll)"
                        + "\\s*(?:\\(|\\b)"),
                Pattern.compile("java\\.util\\.concurrent\\.atomic|"
                        + "\\bAtomic[A-Z][A-Za-z0-9_]*\\b"),
                Pattern.compile("\\b(?:ScheduledExecutorService|"
                        + "ScheduledThreadPoolExecutor|Timer|TimerTask)\\b|"
                        + "(?:\\.|::)\\s*schedule"
                        + "(?:AtFixedRate|WithFixedDelay)?\\s*(?:\\(|\\b)"),
                Pattern.compile("java\\.util\\.concurrent\\.locks\\."
                        + "(?!LockSupport\\b)|\\b(?:ReentrantLock|ReadWriteLock|"
                        + "StampedLock)\\b"));

        sources.forEach((path, source) -> forbidden.forEach(pattern ->
                assertFalse(pattern.matcher(source).find(),
                        path + " matched " + pattern)));

        Path jdkTime = MAIN_PACKAGE.resolve("JdkTime.java");
        sources.forEach((path, source) -> {
            if (path.equals(jdkTime)) {
                assertEquals(1, occurrences(source, "LockSupport::parkNanos"));
                String remaining = source
                        .replace("import java.util.concurrent.locks.LockSupport;", "")
                        .replace("LockSupport::parkNanos", "");
                assertFalse(remaining.contains("LockSupport"), path.toString());
            } else {
                assertFalse(source.contains("LockSupport"), path.toString());
            }
        });

        Path interruptGuard = MAIN_PACKAGE.resolve("InterruptGuard.java");
        Pattern interruptAccess = Pattern.compile(
                "\\.\\s*(?:isInterrupted|interrupted|interrupt)\\s*\\(|"
                        + "::\\s*(?:isInterrupted|interrupted|interrupt)\\b|"
                        + "import\\s+static\\s+java\\.lang\\.Thread\\s*\\.\\s*"
                        + "(?:isInterrupted|interrupted|interrupt)\\s*;");
        sources.forEach((path, source) -> {
            String remaining = source;
            if (path.equals(interruptGuard)) {
                assertEquals(1, occurrences(source,
                        "Thread.currentThread().isInterrupted()"));
                assertEquals(1, occurrences(source,
                        "Thread.currentThread().interrupt()"));
                remaining = remaining
                        .replace("Thread.currentThread().isInterrupted()", "")
                        .replace("Thread.currentThread().interrupt()", "");
            }
            assertFalse(interruptAccess.matcher(remaining).find(), path.toString());
        });
    }

    private static int occurrences(String source, String value) {
        return source.split(Pattern.quote(value), -1).length - 1;
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

    private static Map<Path, String> productionSources(Path root)
            throws IOException {
        Map<Path, String> sources = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java"))
                    .sorted().toList()) {
                sources.put(path, Files.readString(path));
            }
        }
        return sources;
    }

    private static Set<Class<?>> discoveredPublicApiTypes() throws Exception {
        Path classes = Path.of("target", "classes");
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
        types.add(AwaitConfigurationConflictException.class);
        types.addAll(List.of(AwaitFailure.class, AwaitTimeoutException.class,
                AwaitStabilizationException.class,
                AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class));
        return types;
    }

    private static List<Class<?>> publicTypes() {
        return List.of(Assertility.class, AwaitConditions.class,
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
}
