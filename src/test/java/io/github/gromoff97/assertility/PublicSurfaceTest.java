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

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
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
    void productionSourceAuditAllowsUnrelatedNamesCommentsAndStrings() {
        assertApprovedProductionSources(Map.ofEntries(
                Map.entry(Path.of("fixture", "Door.java"), """
                        package fixture;
                        final class Door { void lock() {} }
                        """),
                Map.entry(Path.of("fixture", "Timer.java"), """
                        package fixture;
                        final class Timer { void schedule() {} }
                        """),
                Map.entry(Path.of("fixture", "Allowed.java"), """
                        package fixture;
                        final class Allowed {
                            private final Door door = new Door();
                            private final Timer timer = new Timer();

                            void start() {}
                            void schedule() {}
                            void run() {
                                this.start();
                                schedule();
                                door.lock();
                                timer.schedule();
                                Runnable task = this::schedule;
                                String text = "Thread.sleep synchronized notify";
                                // CompletableFuture worker.start();
                            }
                        }
                        """)));
    }

    @Test
    void productionSourceAuditFailsOnParserAndTypeDiagnostics() {
        for (Map<Path, String> invalid : List.of(
                Map.of(Path.of("Syntax.java"), "final class Syntax {"),
                Map.of(Path.of("Types.java"),
                        "final class Types { MissingType value; }"))) {
            assertThrows(AssertionError.class,
                    () -> assertApprovedProductionSources(invalid));
        }
    }

    @Test
    void productionSourceAuditRejectsForbiddenExecutableSignatures() {
        Map.ofEntries(
                Map.entry("HttpClient async return", """
                        class Mutant {
                            void run(java.net.http.HttpClient client,
                                    java.net.http.HttpRequest request,
                                    java.net.http.HttpResponse.BodyHandler<String> handler) {
                                client.sendAsync(request, handler);
                            }
                        }
                        """),
                Map.entry("asynchronous socket return", """
                        class Mutant {
                            void run(java.nio.channels.AsynchronousSocketChannel channel,
                                    java.net.SocketAddress address) {
                                channel.connect(address);
                            }
                        }
                        """),
                Map.entry("lookup VarHandle return", """
                        class Mutant {
                            int state;
                            void run(java.lang.invoke.MethodHandles.Lookup lookup)
                                    throws ReflectiveOperationException {
                                lookup.findVarHandle(Mutant.class, "state", int.class);
                            }
                        }
                        """),
                Map.entry("array VarHandle return", """
                        class Mutant {
                            void run() {
                                java.lang.invoke.MethodHandles
                                        .arrayElementVarHandle(int[].class);
                            }
                        }
                        """),
                Map.entry("nested generic array return", """
                        package io.github.gromoff97.assertility;
                        class Mutant {
                            Object run() {
                                return PublicSurfaceTest.ExecutableSignatureFixture
                                        .forbiddenGenericArrayReturn();
                            }
                        }
                        """),
                Map.entry("intersection return", """
                        package io.github.gromoff97.assertility;
                        class Mutant {
                            Object run() {
                                return PublicSurfaceTest.ExecutableSignatureFixture
                                        .forbiddenIntersectionReturn();
                            }
                        }
                        """),
                Map.entry("lower wildcard parameter", """
                        package io.github.gromoff97.assertility;
                        class Mutant {
                            void run() {
                                PublicSurfaceTest.ExecutableSignatureFixture
                                        .forbiddenLowerWildcardParameter(null);
                            }
                        }
                        """),
                Map.entry("implicit Executor parameter", """
                        class Mutant {
                            void run() {
                                java.net.http.HttpClient.newBuilder().executor(null);
                            }
                        }
                        """))
                .forEach(PublicSurfaceTest::assertRejectedProductionSource);
    }

    @Test
    void productionSourceAuditRejectsAllJdkSchedulerFamilies() {
        Map.ofEntries(
                Map.entry("util Timer", "class Mutant { java.util.Timer timer; }"),
                Map.entry("util TimerTask", """
                        class Mutant extends java.util.TimerTask {
                            public void run() {}
                        }
                        """),
                Map.entry("Swing Timer subtype", """
                        class Mutant extends javax.swing.Timer {
                            Mutant() { super(1, null); }
                        }
                        """),
                Map.entry("JMX Timer subtype", """
                        class Mutant extends javax.management.timer.Timer {}
                        """),
                Map.entry("JMX TimerMBean", """
                        class Mutant {
                            javax.management.timer.TimerMBean timer;
                        }
                        """))
                .forEach(PublicSurfaceTest::assertRejectedProductionSource);
    }

    @Test
    void productionSourceAuditRejectsEveryApprovedBan() {
        Map<String, String> mutants = Map.ofEntries(
                Map.entry("thread subclass", "class Mutant extends Thread {}"),
                Map.entry("worker start",
                        "class Mutant { void run(Thread worker) { worker.start(); } }"),
                Map.entry("worker start reference",
                        "class Mutant { java.util.function.Consumer<Thread> start = Thread::start; }"),
                Map.entry("thread construction",
                        "class Mutant { Thread worker = new Thread(); }"),
                Map.entry("thread constructor reference",
                        "class Mutant { java.util.function.Function<Runnable, Thread> factory = Thread::new; }"),
                Map.entry("qualified thread constructor reference",
                        "class Mutant { java.util.function.Function<Runnable, Thread> factory = java.lang.Thread::new; }"),
                Map.entry("virtual thread builder",
                        "class Mutant { Thread.Builder.OfVirtual builder = Thread.ofVirtual(); }"),
                Map.entry("platform thread builder",
                        "class Mutant { Thread.Builder.OfPlatform builder = java.lang.Thread.ofPlatform(); }"),
                Map.entry("virtual thread builder reference",
                        "class Mutant { java.util.function.Supplier<Thread.Builder.OfVirtual> builder = Thread::ofVirtual; }"),
                Map.entry("platform thread builder reference",
                        "class Mutant { java.util.function.Supplier<Thread.Builder.OfPlatform> builder = java.lang.Thread::ofPlatform; }"),
                Map.entry("static-imported virtual thread builder", """
                        import static java.lang.Thread.ofVirtual;
                        class Mutant { Thread worker = ofVirtual().unstarted(() -> {}); }
                        """),
                Map.entry("static-imported platform thread builder", """
                        import static java.lang.Thread.ofPlatform;
                        class Mutant { Thread worker = ofPlatform().unstarted(() -> {}); }
                        """),
                Map.entry("static-imported virtual thread starter", """
                        import static java.lang.Thread.startVirtualThread;
                        class Mutant { Thread worker = startVirtualThread(() -> {}); }
                        """),
                Map.entry("virtual thread starter reference", """
                        class Mutant {
                            interface Starter { Thread start(Runnable task); }
                            Starter starter = Thread::startVirtualThread;
                        }
                        """),
                Map.entry("sleep",
                        "class Mutant { void run() throws InterruptedException { Thread.sleep(1); } }"),
                Map.entry("sleep through instance", """
                        class Mutant {
                            void run(Thread thread) throws InterruptedException {
                                thread.sleep(1);
                            }
                        }
                        """),
                Map.entry("qualified sleep",
                        "class Mutant { void run() throws InterruptedException { java.lang.Thread.sleep(1); } }"),
                Map.entry("sleep reference", """
                        class Mutant {
                            interface Sleeper {
                                void sleep(long millis) throws InterruptedException;
                            }
                            Sleeper sleeper = Thread::sleep;
                        }
                        """),
                Map.entry("static-imported sleep", """
                        import static java.lang.Thread.sleep;
                        class Mutant {
                            void run() throws InterruptedException { sleep(1); }
                        }
                        """),
                Map.entry("wildcard static-imported sleep", """
                        import static java.lang.Thread.*;
                        class Mutant {
                            void run() throws InterruptedException { sleep(1); }
                        }
                        """),
                Map.entry("completable future reference",
                        "class Mutant { java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> worker = java.util.concurrent.CompletableFuture::new; }"),
                Map.entry("fork join task API",
                        "class Mutant { java.util.concurrent.ForkJoinTask<?> worker; }"),
                Map.entry("static-imported executor factory", """
                        import static java.util.concurrent.Executors.newFixedThreadPool;
                        class Mutant { Object worker = newFixedThreadPool(1); }
                        """),
                Map.entry("executor method reference", """
                        class Mutant {
                            java.util.concurrent.Executor executor;
                            java.util.function.Consumer<Runnable> submit =
                                    executor::execute;
                        }
                        """),
                Map.entry("interrupt read reference",
                        "class Mutant { java.util.function.BooleanSupplier read = Thread::interrupted; }"),
                Map.entry("interrupt restore reference",
                        "class Mutant { java.util.function.Consumer<Thread> restore = Thread::interrupt; }"),
                Map.entry("static-imported interrupt read", """
                        import static java.lang.Thread.interrupted;
                        class Mutant { boolean read() { return interrupted(); } }
                        """),
                Map.entry("wildcard static-imported interrupt read", """
                        import static java.lang.Thread.*;
                        class Mutant { boolean read() { return interrupted(); } }
                        """),
                Map.entry("qualified interrupt read reference",
                        "class Mutant { java.util.function.BooleanSupplier read = java.lang.Thread::interrupted; }"),
                Map.entry("qualified interrupt restore reference",
                        "class Mutant { java.util.function.Consumer<Thread> restore = java.lang.Thread::interrupt; }"),
                Map.entry("monitor method",
                        "class Mutant { void run(Object lock) throws InterruptedException { lock.wait(); } }"),
                Map.entry("unqualified monitor methods", """
                        class Mutant {
                            void run() throws InterruptedException {
                                wait();
                                notify();
                                notifyAll();
                            }
                        }
                        """),
                Map.entry("monitor method reference",
                        "class Mutant { Runnable notifier = this::notify; }"),
                Map.entry("synchronized monitor",
                        "class Mutant { synchronized void run() {} }"),
                Map.entry("atomic",
                        "class Mutant { java.util.concurrent.atomic.AtomicInteger state; }"),
                Map.entry("simple atomic", """
                        import java.util.concurrent.atomic.AtomicInteger;
                        class Mutant { AtomicInteger state; }
                        """),
                Map.entry("atomic method reference", """
                        class Mutant {
                            java.util.concurrent.atomic.AtomicInteger state;
                            java.util.function.IntSupplier update = state::incrementAndGet;
                        }
                        """),
                Map.entry("var-handle atomic",
                        "class Mutant { java.lang.invoke.VarHandle state; }"),
                Map.entry("scheduler",
                        "class Mutant { java.util.Timer timer; }"),
                Map.entry("lock",
                        "class Mutant { java.util.concurrent.locks.Lock lock; }"),
                Map.entry("simple lock", """
                        import java.util.concurrent.locks.Lock;
                        class Mutant { Lock lock; }
                        """),
                Map.entry("lock call", """
                        class Mutant {
                            void run(java.util.concurrent.locks.Lock lock) {
                                lock.lock();
                                lock.unlock();
                            }
                        }
                        """),
                Map.entry("lock reference", """
                        class Mutant {
                            java.util.concurrent.locks.Lock lock;
                            Runnable acquire = lock::lock;
                        }
                        """),
                Map.entry("LockSupport outside JdkTime",
                        "class Mutant { void run() { java.util.concurrent.locks.LockSupport.park(); } }"),
                Map.entry("interrupt read",
                        "class Mutant { boolean read(Thread t) { return t.isInterrupted(); } }"),
                Map.entry("interrupt restore",
                        "class Mutant { void restore(Thread t) { t.interrupt(); } }"));

        mutants.forEach(PublicSurfaceTest::assertRejectedProductionSource);
        for (String type : List.of("AbstractExecutorService", "Executor",
                "ExecutorCompletionService", "ExecutorService", "Executors",
                "CompletableFuture", "CompletionService", "CompletionStage",
                "Future", "FutureTask", "ForkJoinPool",
                "ScheduledExecutorService", "ScheduledFuture",
                "ScheduledThreadPoolExecutor", "ThreadFactory",
                "ThreadPoolExecutor")) {
            assertRejectedProductionSource("qualified " + type,
                    "class Mutant { java.util.concurrent.%s worker; }"
                            .formatted(type));
            assertRejectedProductionSource("simple " + type, """
                    import java.util.concurrent.%s;
                    class Mutant { %s worker; }
                    """.formatted(type, type));
        }
    }

    private static void assertRejectedProductionSource(
            String name, String source) {
        AssertionError rejection = assertThrows(AssertionError.class,
                () -> assertApprovedProductionSources(
                        Map.of(Path.of(name + ".java"), source)), name);
        assertFalse(rejection.getMessage().startsWith("source diagnostics:"),
                name + " must be rejected semantically");
    }

    private static void assertApprovedProductionSources(
            Map<Path, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("production source audit requires a JDK");
        }
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, null)) {
            List<JavaFileObject> sourceFiles = sources.entrySet().stream()
                    .map(entry -> (JavaFileObject) new SourceFile(
                            entry.getKey(), entry.getValue()))
                    .toList();
            Map<URI, Path> sourcePaths = sources.keySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            source -> source.toAbsolutePath().toUri(),
                            Path::normalize));
            JavacTask task = (JavacTask) compiler.getTask(null, files,
                    diagnostics, List.of("--release", "21", "-proc:none"),
                    null, sourceFiles);
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.analyze();
            List<Diagnostic<? extends JavaFileObject>> errors = diagnostics
                    .getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind()
                            == Diagnostic.Kind.ERROR)
                    .toList();
            if (!errors.isEmpty()) {
                throw new AssertionError("source diagnostics:\n" + errors.stream()
                        .map(diagnostic -> formatDiagnostic(
                                diagnostic, sourcePaths))
                        .collect(java.util.stream.Collectors.joining("\n")));
            }

            ArchitectureAudit audit = new ArchitectureAudit(task, sourcePaths);
            units.forEach(unit -> audit.scan(unit, null));
            audit.verifyApprovedOccurrences();
        } catch (IOException failure) {
            throw new AssertionError("could not audit production sources", failure);
        }
    }

    private static String formatDiagnostic(
            Diagnostic<? extends JavaFileObject> diagnostic,
            Map<URI, Path> sourcePaths) {
        Path path = diagnostic.getSource() == null ? null
                : sourcePaths.get(diagnostic.getSource().toUri());
        String source = path == null ? "<unknown>" : path.toString();
        return source + ":" + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(Path path, String source) {
            super(path.toAbsolutePath().toUri(), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class ArchitectureAudit
            extends TreePathScanner<Void, Void> {

        private static final Path JDK_TIME =
                MAIN_PACKAGE.resolve("JdkTime.java").normalize();
        private static final Path INTERRUPT_GUARD =
                MAIN_PACKAGE.resolve("InterruptGuard.java").normalize();
        private static final String THREAD = "java.lang.Thread";
        private static final String LOCK_SUPPORT =
                "java.util.concurrent.locks.LockSupport";
        private static final Set<String> THREAD_WORK_METHODS = Set.of(
                "sleep", "start", "ofVirtual", "ofPlatform",
                "startVirtualThread");
        private static final Set<String> INTERRUPT_METHODS = Set.of(
                "isInterrupted", "interrupted", "interrupt");
        private static final Set<String> MONITOR_METHODS = Set.of(
                "wait", "notify", "notifyAll");

        private final Trees trees;
        private final Types types;
        private final Elements elements;
        private final Map<URI, Path> sourcePaths;
        private final TypeMirror threadType;
        private final List<TypeMirror> forbiddenRoots;
        private final Set<Path> auditedPaths = new java.util.HashSet<>();
        private CompilationUnitTree unit;
        private Path path;
        private int parks;
        private int interruptReads;
        private int interruptRestores;

        private ArchitectureAudit(JavacTask task, Map<URI, Path> sourcePaths) {
            trees = Trees.instance(task);
            types = task.getTypes();
            elements = task.getElements();
            this.sourcePaths = sourcePaths;
            threadType = requiredType(THREAD);
            forbiddenRoots = List.of(
                    requiredType("java.util.concurrent.Executor"),
                    requiredType("java.util.concurrent.Future"),
                    requiredType("java.util.concurrent.CompletionStage"),
                    requiredType("java.util.concurrent.CompletionService"),
                    requiredType("java.util.concurrent.ThreadFactory"),
                    requiredType("java.util.Timer"),
                    requiredType("java.util.TimerTask"),
                    requiredType("javax.swing.Timer"),
                    requiredType("javax.management.timer.TimerMBean"));
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree tree, Void unused) {
            unit = tree;
            path = sourcePaths.get(tree.getSourceFile().toUri());
            if (path == null) {
                throw new AssertionError("unknown source: "
                        + tree.getSourceFile().toUri());
            }
            auditedPaths.add(path);
            return super.visitCompilationUnit(tree, unused);
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type) {
                if (isAssignable(type.asType(), threadType)) {
                    reject(tree, "Thread subclass");
                }
                if (isForbiddenMechanism(type)) {
                    reject(tree, "concurrency mechanism subtype "
                            + type.getQualifiedName());
                }
            }
            return super.visitClass(tree, unused);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            if (tree.getModifiers().getFlags().contains(
                    javax.lang.model.element.Modifier.SYNCHRONIZED)) {
                reject(tree, "synchronized method");
            }
            return super.visitMethod(tree, unused);
        }

        @Override
        public Void visitSynchronized(SynchronizedTree tree, Void unused) {
            reject(tree, "synchronized block");
            return null;
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void unused) {
            checkExecutable(tree, trees.getElement(getCurrentPath()));
            return super.visitNewClass(tree, unused);
        }

        @Override
        public Void visitMethodInvocation(
                MethodInvocationTree tree, Void unused) {
            checkExecutable(tree, trees.getElement(getCurrentPath()));
            return super.visitMethodInvocation(tree, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
            checkExecutable(tree, trees.getElement(getCurrentPath()));
            return super.visitMemberReference(tree, unused);
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            checkTypeReference(tree);
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            checkTypeReference(tree);
            return super.visitMemberSelect(tree, unused);
        }

        private void checkTypeReference(Tree tree) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof TypeElement type)) {
                return;
            }
            String name = type.getQualifiedName().toString();
            if (name.equals(LOCK_SUPPORT)) {
                if (!isApprovedLockSupportTypeReference(tree)) {
                    reject(tree, "LockSupport outside the approved parkNanos port");
                }
            } else if (isForbiddenMechanism(type)) {
                reject(tree, "forbidden concurrency type " + name);
            }
        }

        private void checkExecutable(Tree tree, Element element) {
            if (!(element instanceof ExecutableElement executable)
                    || !(executable.getEnclosingElement()
                    instanceof TypeElement owner)) {
                return;
            }
            String ownerName = owner.getQualifiedName().toString();
            String name = executable.getSimpleName().toString();

            if (hasForbiddenSignature(resolvedExecutableType(tree, executable))
                    || hasForbiddenSignature(
                    (ExecutableType) executable.asType())) {
                reject(tree, "forbidden concurrency type in executable "
                        + ownerName + "." + name);
            }
            if (executable.getKind()
                    == javax.lang.model.element.ElementKind.CONSTRUCTOR) {
                if (isAssignable(owner.asType(), threadType)) {
                    reject(tree, "Thread construction");
                }
                if (isForbiddenMechanism(owner)) {
                    reject(tree, "construction of " + ownerName);
                }
                return;
            }
            if (ownerName.equals(LOCK_SUPPORT)) {
                if (tree instanceof MemberReferenceTree reference
                        && name.equals("parkNanos")
                        && path.equals(JDK_TIME)
                        && reference.getMode()
                        == MemberReferenceTree.ReferenceMode.INVOKE) {
                    parks++;
                    return;
                }
                reject(tree, "LockSupport outside JdkTime::parkNanos");
            }
            if (INTERRUPT_METHODS.contains(name)
                    && isAssignable(owner.asType(), threadType)) {
                if (tree instanceof MethodInvocationTree invocation
                        && isApprovedInterruptCall(invocation, name)) {
                    if (name.equals("isInterrupted")) {
                        interruptReads++;
                    } else {
                        interruptRestores++;
                    }
                    return;
                }
                reject(tree, "Thread interrupt access outside InterruptGuard");
            }
            if (THREAD_WORK_METHODS.contains(name)
                    && isAssignable(owner.asType(), threadType)) {
                reject(tree, "Thread worker mechanism " + name);
            }
            if (ownerName.equals("java.lang.Object")
                    && MONITOR_METHODS.contains(name)) {
                reject(tree, "Object monitor method " + name);
            }
            if (isForbiddenMechanism(owner)) {
                reject(tree, "concurrency mechanism " + ownerName + "." + name);
            }
        }

        private ExecutableType resolvedExecutableType(
                Tree tree, ExecutableElement fallback) {
            if (tree instanceof MethodInvocationTree invocation) {
                TypeMirror resolved = trees.getTypeMirror(new TreePath(
                        getCurrentPath(), invocation.getMethodSelect()));
                if (resolved instanceof ExecutableType executable) {
                    return executable;
                }
            }
            return (ExecutableType) fallback.asType();
        }

        private boolean hasForbiddenSignature(ExecutableType executable) {
            Set<TypeMirror> visited = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            return hasForbiddenType(executable.getReturnType(), visited)
                    || executable.getParameterTypes().stream()
                    .anyMatch(type -> hasForbiddenType(type, visited));
        }

        private boolean hasForbiddenType(
                TypeMirror type, Set<TypeMirror> visited) {
            if (!visited.add(type)) {
                return false;
            }
            return switch (type.getKind()) {
                case ARRAY -> hasForbiddenType(
                        ((ArrayType) type).getComponentType(), visited);
                case DECLARED -> {
                    DeclaredType declared = (DeclaredType) type;
                    yield declared.asElement() instanceof TypeElement element
                            && isForbiddenMechanism(element)
                            || hasForbiddenType(declared.getEnclosingType(), visited)
                            || declared.getTypeArguments().stream()
                            .anyMatch(argument -> hasForbiddenType(
                                    argument, visited));
                }
                case TYPEVAR -> {
                    javax.lang.model.type.TypeVariable variable =
                            (javax.lang.model.type.TypeVariable) type;
                    yield hasForbiddenType(variable.getUpperBound(), visited)
                            || hasForbiddenType(variable.getLowerBound(), visited);
                }
                case WILDCARD -> {
                    javax.lang.model.type.WildcardType wildcard =
                            (javax.lang.model.type.WildcardType) type;
                    yield wildcard.getExtendsBound() != null
                            && hasForbiddenType(
                                    wildcard.getExtendsBound(), visited)
                            || wildcard.getSuperBound() != null
                            && hasForbiddenType(
                                    wildcard.getSuperBound(), visited);
                }
                case INTERSECTION -> ((IntersectionType) type).getBounds().stream()
                        .anyMatch(bound -> hasForbiddenType(bound, visited));
                default -> false;
            };
        }

        private boolean isApprovedLockSupportTypeReference(Tree tree) {
            if (!path.equals(JDK_TIME)) {
                return false;
            }
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (parent instanceof ImportTree imported) {
                return !imported.isStatic();
            }
            if (!(parent instanceof MemberReferenceTree reference)
                    || reference.getQualifierExpression() != tree) {
                return false;
            }
            Element element = trees.getElement(getCurrentPath().getParentPath());
            return element instanceof ExecutableElement executable
                    && executable.getSimpleName().contentEquals("parkNanos")
                    && executable.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals(LOCK_SUPPORT);
        }

        private boolean isApprovedInterruptCall(
                MethodInvocationTree invocation, String name) {
            if (!path.equals(INTERRUPT_GUARD)
                    || !(name.equals("isInterrupted") || name.equals("interrupt"))
                    || !invocation.getArguments().isEmpty()
                    || !(invocation.getMethodSelect()
                    instanceof MemberSelectTree selected)
                    || !(selected.getExpression()
                    instanceof MethodInvocationTree currentThread)
                    || !currentThread.getArguments().isEmpty()) {
                return false;
            }
            TreePath selectedPath = new TreePath(
                    getCurrentPath(), invocation.getMethodSelect());
            Element currentThreadElement = trees.getElement(new TreePath(
                    selectedPath, selected.getExpression()));
            return currentThreadElement instanceof ExecutableElement executable
                    && executable.getSimpleName().contentEquals("currentThread")
                    && executable.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals(THREAD);
        }

        private boolean isForbiddenMechanism(TypeElement type) {
            String name = type.getQualifiedName().toString();
            String packageName = elements.getPackageOf(type)
                    .getQualifiedName().toString();
            return name.equals("java.util.concurrent.Executors")
                    || name.equals("java.lang.invoke.VarHandle")
                    || name.equals("java.lang.Thread.Builder")
                    || name.startsWith("java.lang.Thread.Builder.")
                    || packageName.equals("java.util.concurrent.atomic")
                    || packageName.equals("java.util.concurrent.locks")
                    && !name.equals(LOCK_SUPPORT)
                    || forbiddenRoots.stream()
                    .anyMatch(root -> isAssignable(type.asType(), root));
        }

        private boolean isAssignable(TypeMirror candidate, TypeMirror target) {
            return candidate.getKind() == TypeKind.DECLARED
                    && types.isAssignable(types.erasure(candidate),
                    types.erasure(target));
        }

        private TypeMirror requiredType(String name) {
            TypeElement type = elements.getTypeElement(name);
            if (type == null) {
                throw new AssertionError("JDK type not found: " + name);
            }
            return type.asType();
        }

        private void reject(Tree tree, String reason) {
            long position = trees.getSourcePositions()
                    .getStartPosition(unit, tree);
            long line = position < 0 ? -1 : unit.getLineMap()
                    .getLineNumber(position);
            throw new AssertionError(path + ":" + line + ": " + reason);
        }

        private void verifyApprovedOccurrences() {
            if (auditedPaths.contains(JDK_TIME) && parks != 1) {
                throw new AssertionError(JDK_TIME
                        + ": expected exactly one LockSupport::parkNanos");
            }
            if (auditedPaths.contains(INTERRUPT_GUARD)
                    && (interruptReads != 1 || interruptRestores != 1)) {
                throw new AssertionError(INTERRUPT_GUARD
                        + ": expected exactly one interrupt read and restoration");
            }
        }
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

    public static final class ExecutableSignatureFixture {
        public static List<? extends Future<?>[]> forbiddenGenericArrayReturn() {
            return null;
        }

        public static <T extends Future<?> & java.io.Serializable>
                T forbiddenIntersectionReturn() {
            return null;
        }

        public static void forbiddenLowerWildcardParameter(
                List<? super java.util.concurrent.ForkJoinTask<?>> value) {
        }

        private ExecutableSignatureFixture() {
        }
    }
}
