package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.writeString;
import static java.util.Collections.newSetFromMap;
import static org.openrewrite.Parser.Input.fromString;
import static org.openrewrite.java.JavaParser.fromJavaVersion;
import static org.openrewrite.java.search.FindMissingTypes.findMissingTypes;
import static org.openrewrite.java.tree.TypeUtils.asFullyQualified;
import static org.openrewrite.java.tree.TypeUtils.isAssignableTo;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;
import static org.openrewrite.java.tree.TypeUtils.isWellFormedType;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMissingTypes.MissingTypeResult;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.tree.ParseError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureContractTest {

    private static final Path MAIN_PACKAGE = Path.of("src", "main", "java", "io",
            "github", "gromoff97", "awium");

    @Test
    void waitingCoreLivesInTheDedicatedEnginePackage() {
        for (String type : List.of("Attempt", "WaitOutcome", "WaitEngine",
                "WaitConfiguration")) {
            assertDoesNotThrow(() -> Class.forName(
                    "io.github.gromoff97.awium.engine." + type), type);
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.gromoff97.awium.internal.engine.DurationFormatter"));
        for (String type : List.of("AttemptEvaluator", "AttemptResult",
                "Interrupts", "WaitConfiguration", "WaitEngine",
                "WaitResult")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(
                    "io.github.gromoff97.awium.internal.engine." + type), type);
        }
    }

    @Test
    void failureRenderingLivesInTheDedicatedDiagnosticsPackage() {
        for (String type : List.of("FailureFactory", "FailureMessage")) {
            assertDoesNotThrow(() -> Class.forName(
                    "io.github.gromoff97.awium.diagnostics." + type),
                    type);
        }
        for (String type : List.of("FailureFactory", "FailureContext",
                "Diagnostics", "ValueRenderer")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(
                    "io.github.gromoff97.awium.internal.diagnostic." + type),
                    type);
        }
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.github.gromoff97.awium."
                        + "DiagnosticFormatter"));
    }

    @Test
    void fluentImplementationUsesThreeApiInterfacesAndMinimalAdapters() {
        for (String type : List.of(
                "io.github.gromoff97.awium.await.Await",
                "io.github.gromoff97.awium.await.OptionalAwait",
                "io.github.gromoff97.awium.await.StructuralAwait",
                "io.github.gromoff97.awium.conditioning.conditions.Condition$ExplainedCondition",
                "io.github.gromoff97.awium.conditioning.conditions.PreservingCondition$ExplainedCondition",
                "io.github.gromoff97.awium.conditioning.conditions.PresentCondition$ExplainedCondition",
                "io.github.gromoff97.awium.conditioning.conditions.StructuralCondition$ExplainedCondition")) {
            assertDoesNotThrow(() -> Class.forName(type), type);
        }
        for (String type : List.of("ObjectUntil", "OptionalUntil",
                "CollectionUntil", "SequencedCollectionUntil", "MapUntil",
                "ExplainedCondition", "ExplainedPreservingCondition",
                "ExplainedPresent", "ExplainedStructuralCondition",
                "ConditionAdapters")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(
                    "io.github.gromoff97.awium." + type), type);
        }
    }

    @Test
    void architectureAuditRejectsObsoleteProductTypes() {
        for (String type : List.of("AwaitSources", "ObjectAwait",
                "CollectionAwait", "MapAwait", "SequencedCollectionAwait",
                "AttemptResult", "WaitResult")) {
            assertRejected("obsolete " + type,
                    "final class " + type + " {}");
        }
    }

    @Test
    void productionSourcesUseOnlyApprovedWaitingAndInterruptionMechanics()
            throws IOException {
        assertApprovedSources(productionSources(MAIN_PACKAGE));
    }

    @Test
    void architectureAuditRecursesIntoSubpackages(@TempDir Path root)
            throws IOException {
        Path nested = root.resolve("worker").resolve("Mutant.java");
        createDirectories(nested.getParent());
        writeString(nested, """
                final class Mutant extends Thread {
                }
                """);

        assertThrows(AssertionError.class,
                () -> assertApprovedSources(productionSources(root)));
    }

    @Test
    void architectureAuditAllowsUnrelatedNamesCommentsAndStrings() {
        assertApprovedSources(Map.ofEntries(
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
                            void sleep() {}
                            void run() {
                                this.start();
                                schedule();
                                sleep();
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
    void architectureAuditFailsOnParserAndTypeErrors() {
        for (Map<Path, String> invalid : List.of(
                Map.of(Path.of("Syntax.java"), "final class Syntax {"),
                Map.of(Path.of("Types.java"),
                        "final class Types { MissingType value; }"),
                Map.of(Path.of("Import.java"),
                        "import missing.Type; final class Import {}"),
                Map.of(Path.of("StaticImport.java"),
                        "import static missing.Type.method; "
                                + "final class StaticImport {}"),
                Map.of(Path.of("Call.java"),
                        "final class Call { void run() { missing(); } }"),
                Map.of(Path.of("Cast.java"),
                        "final class Cast { Object value = (MissingType) null; }"),
                Map.of(Path.of("ClassLiteral.java"),
                        "final class ClassLiteral { Class<?> type = MissingType.class; }"),
                Map.of(Path.of("Annotation.java"),
                        "@Missing final class Annotation {}"),
                Map.of(Path.of("Throws.java"),
                        "final class Throws { void run() throws MissingType {} }"),
                Map.of(Path.of("InstanceOf.java"),
                        "final class InstanceOf { boolean test(Object value) { return value instanceof MissingType; } }"))) {
            assertThrows(AssertionError.class,
                    () -> assertApprovedSources(invalid));
        }
    }

    @Test
    void architectureAuditRejectsForbiddenResolvedExecutableTypes() {
        Map.ofEntries(
                Map.entry("implicit future return", """
                        class Mutant {
                            void run(java.net.http.HttpClient client,
                                    java.net.http.HttpRequest request) {
                                client.sendAsync(request,
                                        java.net.http.HttpResponse.BodyHandlers.ofString());
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
                        class Mutant {
                            static java.util.List<? extends
                                    java.util.concurrent.Future<?>[]>
                                    forbiddenGenericArrayReturn() {
                                return null;
                            }
                            Object run() {
                                return forbiddenGenericArrayReturn();
                            }
                        }
                        """),
                Map.entry("intersection return", """
                        class Mutant {
                            static <T extends java.util.concurrent.Future<?>
                                    & java.io.Serializable>
                                    T forbiddenIntersectionReturn() {
                                return null;
                            }
                            Object run() {
                                return forbiddenIntersectionReturn();
                            }
                        }
                        """),
                Map.entry("lower wildcard parameter", """
                        class Mutant {
                            static void forbiddenLowerWildcardParameter(
                                    java.util.List<? super
                                    java.util.concurrent.ForkJoinTask<?>> value) {
                            }
                            void run() {
                                forbiddenLowerWildcardParameter(null);
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
                .forEach(ArchitectureContractTest::assertRejected);
    }

    @Test
    void architectureAuditRejectsAllJdkSchedulerFamilies() {
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
                .forEach(ArchitectureContractTest::assertRejected);
    }

    @Test
    void architectureAuditRejectsEveryApprovedBan() {
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
                Map.entry("instance-qualified sleep", """
                        class Mutant {
                            void run(Thread thread) throws Exception { thread.sleep(1); }
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
                Map.entry("LockSupport outside the approved park port",
                        "class Mutant { void run() { java.util.concurrent.locks.LockSupport.park(); } }"),
                Map.entry("interrupt read",
                        "class Mutant { boolean read(Thread t) { return t.isInterrupted(); } }"),
                Map.entry("interrupt restore",
                        "class Mutant { void restore(Thread t) { t.interrupt(); } }"));

        mutants.forEach(ArchitectureContractTest::assertRejected);
        for (String type : List.of("AbstractExecutorService", "Executor",
                "ExecutorCompletionService", "ExecutorService", "Executors",
                "CompletableFuture", "CompletionService", "CompletionStage",
                "Future", "FutureTask", "ForkJoinPool",
                "ScheduledExecutorService", "ScheduledFuture",
                "ScheduledThreadPoolExecutor", "ThreadFactory",
                "ThreadPoolExecutor")) {
            assertRejected("qualified " + type,
                    "class Mutant { java.util.concurrent.%s worker; }"
                            .formatted(type));
            assertRejected("simple " + type, """
                    import java.util.concurrent.%s;
                    class Mutant { %s worker; }
                    """.formatted(type, type));
        }
    }

    @Test
    void architectureAuditAllowsExactlyApprovedPorts() {
        assertApprovedSources(Map.of(
                MAIN_PACKAGE.resolve("await/stages/AbstractAwaitStage.java"), """
                        package io.github.gromoff97.awium.await.stages;
                        import java.util.concurrent.locks.LockSupport;
                        abstract class AbstractAwaitStage {
                            java.util.function.LongConsumer parker =
                                    LockSupport::parkNanos;
                        }
                        """,
                MAIN_PACKAGE.resolve("engine/WaitEngine.java"), """
                        package io.github.gromoff97.awium.engine;
                        final class WaitEngine {
                            boolean read() {
                                return Thread.currentThread().isInterrupted();
                            }
                            void restore() {
                                Thread.currentThread().interrupt();
                            }
                        }
                        """));

        assertRejectedAt(MAIN_PACKAGE.resolve(
                "await/stages/AbstractAwaitStage.java"), """
                package io.github.gromoff97.awium.await.stages;
                abstract class AbstractAwaitStage {}
                """);
        assertRejectedAt(MAIN_PACKAGE.resolve(
                "await/stages/AbstractAwaitStage.java"), """
                package io.github.gromoff97.awium.await.stages;
                import java.util.concurrent.locks.LockSupport;
                abstract class AbstractAwaitStage {
                    interface BlockerParker {
                        void park(Object blocker, long nanos);
                    }
                    BlockerParker parker = LockSupport::parkNanos;
                }
                """);
        assertRejectedAt(MAIN_PACKAGE.resolve(
                "engine/WaitEngine.java"), """
                package io.github.gromoff97.awium.engine;
                final class WaitEngine {
                    boolean first() {
                        return Thread.currentThread().isInterrupted();
                    }
                    boolean second() {
                        return Thread.currentThread().isInterrupted();
                    }
                    void restore() {
                        Thread.currentThread().interrupt();
                    }
                }
                """);
    }

    private static void assertRejected(String name, String source) {
        AssertionError rejection = assertThrows(AssertionError.class,
                () -> assertApprovedSources(
                        Map.of(Path.of(name + ".java"), source)), name);
        assertFalse(rejection.getMessage().contains("OpenRewrite parsing failed"),
                name + " must be rejected semantically");
        assertFalse(rejection.getMessage().contains("type attribution failed"),
                name + " must be rejected semantically");
    }

    private static void assertRejectedAt(Path path, String source) {
        assertThrows(AssertionError.class,
                () -> assertApprovedSources(Map.of(path, source)));
    }

    private static void assertApprovedSources(Map<Path, String> sources) {
        ExecutionContext context = new InMemoryExecutionContext(failure -> {
            throw new AssertionError("OpenRewrite parsing failed", failure);
        });
        JavaParser parser = fromJavaVersion().build();
        List<SourceFile> parsed = parser.parseInputs(
                sources.entrySet().stream()
                        .map(entry -> fromString(
                                entry.getKey(), entry.getValue()))
                        .toList(),
                Path.of(""),
                context).toList();
        if (parsed.size() != sources.size()) {
            throw new AssertionError("OpenRewrite parsing failed: expected "
                    + sources.size() + " sources but parsed " + parsed.size());
        }
        parsed.stream().filter(ParseError.class::isInstance)
                .map(ParseError.class::cast).findFirst().ifPresent(error -> {
                    throw new AssertionError(error.getSourcePath()
                            + ": OpenRewrite parsing failed", error.toException());
                });

        ArchitectureVisitor visitor = new ArchitectureVisitor();
        for (SourceFile source : parsed) {
            if (!(source instanceof J.CompilationUnit unit)) {
                throw new AssertionError(source.getSourcePath()
                        + ": expected a typed Java compilation unit");
            }
            List<MissingTypeResult> missingTypes = findMissingTypes(unit, false);
            if (!missingTypes.isEmpty()) {
                throw new AssertionError(source.getSourcePath()
                        + ": OpenRewrite type attribution failed: "
                        + missingTypes.getFirst().getMessage());
            }
            try {
                visitor.visit(unit, context);
            } catch (RuntimeException failure) {
                for (Throwable cause = failure.getCause(); cause != null;
                        cause = cause.getCause()) {
                    if (cause instanceof AssertionError rejection) {
                        throw rejection;
                    }
                }
                throw failure;
            }
        }
        visitor.verifyApprovedOccurrences();
    }

    private static final class ArchitectureVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final Path PARK_PORT =
                MAIN_PACKAGE.resolve(
                        "await/stages/AbstractAwaitStage.java").normalize();
        private static final Path INTERRUPT_PORT = MAIN_PACKAGE
                .resolve("engine/WaitEngine.java").normalize();
        private static final String THREAD = "java.lang.Thread";
        private static final String LOCK_SUPPORT =
                "java.util.concurrent.locks.LockSupport";
        private static final MethodMatcher THREAD_METHOD =
                new MethodMatcher("java.lang.Thread *(..)", true);
        private static final MethodMatcher OBJECT_METHOD =
                new MethodMatcher("java.lang.Object *(..)", true);
        private static final MethodMatcher LOCK_SUPPORT_METHOD =
                new MethodMatcher(LOCK_SUPPORT + " *(..)");
        private static final MethodMatcher PARK_NANOS =
                new MethodMatcher(LOCK_SUPPORT + " parkNanos(long)");
        private static final MethodMatcher CURRENT_THREAD =
                new MethodMatcher("java.lang.Thread currentThread()");
        private static final Set<String> THREAD_WORK_METHODS = Set.of(
                "sleep", "start", "ofVirtual", "ofPlatform",
                "startVirtualThread");
        private static final Set<String> INTERRUPT_METHODS = Set.of(
                "isInterrupted", "interrupted", "interrupt");
        private static final Set<String> MONITOR_METHODS = Set.of(
                "wait", "notify", "notifyAll");
        private static final Set<String> OBSOLETE_PRODUCT_TYPES = Set.of(
                "AwaitSources", "ObjectAwait", "CollectionAwait", "MapAwait",
                "SequencedCollectionAwait", "AttemptResult", "WaitResult");
        private static final List<String> FORBIDDEN_ROOTS = List.of(
                "java.util.concurrent.Executor",
                "java.util.concurrent.Future",
                "java.util.concurrent.CompletionStage",
                "java.util.concurrent.CompletionService",
                "java.util.concurrent.ThreadFactory",
                "java.util.Timer",
                "java.util.TimerTask",
                "javax.swing.Timer",
                "javax.management.timer.TimerMBean");

        private final Set<Path> auditedPaths = new java.util.HashSet<>();
        private int parks;
        private int interruptReads;
        private int interruptRestores;

        @Override
        public J.CompilationUnit visitCompilationUnit(
                J.CompilationUnit unit, ExecutionContext context) {
            auditedPaths.add(unit.getSourcePath().normalize());
            return super.visitCompilationUnit(unit, context);
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration declaration, ExecutionContext context) {
            requireType(declaration, declaration.getType(), "class declaration");
            if (OBSOLETE_PRODUCT_TYPES.contains(declaration.getSimpleName())) {
                reject(declaration, "obsolete product type "
                        + declaration.getSimpleName());
            }
            if (isAssignableTo(THREAD, declaration.getType())) {
                reject(declaration, "Thread subclass");
            }
            if (isForbiddenMechanism(declaration.getType())) {
                reject(declaration, "forbidden concurrency mechanism subtype");
            }
            return super.visitClassDeclaration(declaration, context);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(
                J.MethodDeclaration declaration, ExecutionContext context) {
            JavaType.Method method = declaration.getMethodType();
            requireMethod(declaration, method);
            checkSignature(declaration, method);
            if (method.hasFlags(Flag.Synchronized)) {
                reject(declaration, "synchronized method");
            }
            return super.visitMethodDeclaration(declaration, context);
        }

        @Override
        public J.Synchronized visitSynchronized(
                J.Synchronized synchronizedBlock, ExecutionContext context) {
            reject(synchronizedBlock, "synchronized block");
            return synchronizedBlock;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations declarations, ExecutionContext context) {
            JavaType type = declarations.getType();
            if (getCursor().firstEnclosing(J.Lambda.Parameters.class) == null) {
                requireType(declarations, type, "variable declaration");
            }
            if (type != null && isWellFormedType(type)) {
                checkForbiddenType(declarations, type,
                        "forbidden concurrency type in declaration");
            }
            return super.visitVariableDeclarations(declarations, context);
        }

        @Override
        public J.NewClass visitNewClass(
                J.NewClass construction, ExecutionContext context) {
            JavaType.Method constructor = construction.getConstructorType();
            checkExecutable(construction, constructor);
            if (isAssignableTo(THREAD, construction.getType())) {
                reject(construction, "Thread construction");
            }
            return super.visitNewClass(construction, context);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(
                J.MethodInvocation invocation, ExecutionContext context) {
            checkExecutable(invocation, invocation.getMethodType());
            return super.visitMethodInvocation(invocation, context);
        }

        @Override
        public J.MemberReference visitMemberReference(
                J.MemberReference reference, ExecutionContext context) {
            checkExecutable(reference, reference.getMethodType());
            return super.visitMemberReference(reference, context);
        }

        @Override
        public J.Identifier visitIdentifier(
                J.Identifier identifier, ExecutionContext context) {
            JavaType type = identifier.getType();
            if (type != null && isWellFormedType(type)) {
                if (isLockSupport(type)) {
                    if (!isApprovedLockSupportReference()) {
                        reject(identifier,
                                "LockSupport outside the approved parkNanos port");
                    }
                } else {
                    checkForbiddenType(identifier, type,
                            "forbidden concurrency type reference");
                }
            }
            return super.visitIdentifier(identifier, context);
        }

        @Override
        public J.Import visitImport(J.Import imported, ExecutionContext context) {
            JavaType type = imported.isStatic()
                    ? imported.getQualid().getTarget().getType()
                    : imported.getQualid().getType();
            requireType(imported, type, "import");
            return super.visitImport(imported, context);
        }

        @Override
        public J.FieldAccess visitFieldAccess(
                J.FieldAccess access, ExecutionContext context) {
            JavaType type = access.getType();
            if (type != null && isWellFormedType(type)) {
                if (isLockSupport(type)) {
                    if (!isApprovedLockSupportReference()) {
                        reject(access,
                                "LockSupport outside the approved parkNanos port");
                    }
                } else {
                    checkForbiddenType(access, type,
                            "forbidden concurrency type reference");
                }
            }
            return super.visitFieldAccess(access, context);
        }

        private void checkExecutable(J tree, JavaType.Method method) {
            requireMethod(tree, method);
            checkSignature(tree, method);
            JavaType.FullyQualified owner = method.getDeclaringType();
            String name = method.getName();

            if (LOCK_SUPPORT_METHOD.matches(method)) {
                if (tree instanceof J.MemberReference
                        && PARK_NANOS.matches(method)
                        && currentPath().equals(PARK_PORT)) {
                    parks++;
                    return;
                }
                reject(tree, "LockSupport outside the approved parkNanos port");
            }
            if (THREAD_METHOD.matches(method)
                    && INTERRUPT_METHODS.contains(name)) {
                if (tree instanceof J.MethodInvocation invocation
                        && isApprovedInterruptCall(invocation, name)) {
                    if (name.equals("isInterrupted")) {
                        interruptReads++;
                    } else {
                        interruptRestores++;
                    }
                    return;
                }
                reject(tree, "Thread interrupt access outside WaitEngine");
            }
            if (THREAD_METHOD.matches(method)
                    && THREAD_WORK_METHODS.contains(name)) {
                reject(tree, "Thread worker mechanism " + name);
            }
            if (OBJECT_METHOD.matches(method)
                    && MONITOR_METHODS.contains(name)) {
                reject(tree, "Object monitor method " + name);
            }
            if (method.isConstructor()
                    && isAssignableTo(THREAD, owner)) {
                reject(tree, "Thread construction");
            }
            if (isForbiddenMechanism(owner)) {
                reject(tree, "concurrency mechanism "
                        + owner.getFullyQualifiedName() + "." + name);
            }
        }

        private void checkSignature(J tree, JavaType.Method method) {
            Set<JavaType> visited = newSetFromMap(
                    new IdentityHashMap<>());
            if (hasForbiddenType(method.getReturnType(), visited)
                    || method.getParameterTypes().stream()
                    .anyMatch(type -> hasForbiddenType(type, visited))) {
                reject(tree, "forbidden concurrency type in executable "
                        + method.getDeclaringType().getFullyQualifiedName()
                        + "." + method.getName());
            }
        }

        private void checkForbiddenType(
                J tree, JavaType type, String reason) {
            if (hasForbiddenType(type, newSetFromMap(
                    new IdentityHashMap<>()))) {
                reject(tree, reason);
            }
        }

        private boolean hasForbiddenType(
                JavaType type, Set<JavaType> visited) {
            if (type == null || !visited.add(type)) {
                return false;
            }
            if (isForbiddenMechanism(type)) {
                return true;
            }
            if (type instanceof JavaType.Array array) {
                return hasForbiddenType(array.getElemType(), visited);
            }
            if (type instanceof JavaType.Parameterized parameterized) {
                return hasForbiddenType(parameterized.getType(), visited)
                        || parameterized.getTypeParameters().stream()
                        .anyMatch(parameter -> hasForbiddenType(
                                parameter, visited));
            }
            if (type instanceof JavaType.GenericTypeVariable variable) {
                return variable.getBounds().stream()
                        .anyMatch(bound -> hasForbiddenType(bound, visited));
            }
            if (type instanceof JavaType.Variable variable) {
                return hasForbiddenType(variable.getType(), visited);
            }
            if (type instanceof JavaType.MultiCatch multiCatch) {
                return multiCatch.getThrowableTypes().stream()
                        .anyMatch(thrown -> hasForbiddenType(thrown, visited));
            }
            return false;
        }

        private boolean isForbiddenMechanism(JavaType type) {
            JavaType.FullyQualified fullyQualified =
                    asFullyQualified(type);
            if (fullyQualified == null) {
                return false;
            }
            String name = fullyQualified.getFullyQualifiedName();
            return name.equals("java.util.concurrent.Executors")
                    || name.equals("java.lang.invoke.VarHandle")
                    || name.startsWith("java.lang.Thread$Builder")
                    || name.startsWith("java.lang.Thread.Builder")
                    || name.startsWith("java.util.concurrent.atomic.")
                    || name.startsWith("java.util.concurrent.locks.")
                    && !name.equals(LOCK_SUPPORT)
                    || FORBIDDEN_ROOTS.stream()
                    .anyMatch(root -> isAssignableTo(root, type));
        }

        private boolean isLockSupport(JavaType type) {
            return isOfClassType(type, LOCK_SUPPORT);
        }

        private boolean isApprovedLockSupportReference() {
            if (!currentPath().equals(PARK_PORT)) {
                return false;
            }
            J.Import imported = getCursor().firstEnclosing(J.Import.class);
            if (imported != null) {
                return !imported.isStatic();
            }
            J.MemberReference reference = getCursor()
                    .firstEnclosing(J.MemberReference.class);
            return reference != null
                    && reference.getMethodType() != null
                    && PARK_NANOS.matches(reference.getMethodType());
        }

        private boolean isApprovedInterruptCall(
                J.MethodInvocation invocation, String name) {
            return currentPath().equals(INTERRUPT_PORT)
                    && (name.equals("isInterrupted") || name.equals("interrupt"))
                    && hasNoArguments(invocation)
                    && invocation.getSelect() instanceof J.MethodInvocation current
                    && hasNoArguments(current)
                    && CURRENT_THREAD.matches(current);
        }

        private boolean hasNoArguments(J.MethodInvocation invocation) {
            return invocation.getArguments().isEmpty()
                    || invocation.getArguments().size() == 1
                    && invocation.getArguments().getFirst() instanceof J.Empty;
        }

        private void requireMethod(J tree, JavaType.Method method) {
            if (method == null || !isWellFormedType(method)
                    || method.getDeclaringType() == null) {
                reject(tree, "OpenRewrite type attribution failed for executable");
            }
        }

        private void requireType(J tree, JavaType type, String description) {
            if (type == null || !isWellFormedType(type)) {
                reject(tree, "OpenRewrite type attribution failed for "
                        + description);
            }
        }

        private Path currentPath() {
            return getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                    .getSourcePath().normalize();
        }

        private void reject(J tree, String reason) {
            throw new AssertionError(currentPath() + ": " + reason);
        }

        private void verifyApprovedOccurrences() {
            if (auditedPaths.contains(PARK_PORT) && parks != 1) {
                throw new AssertionError(PARK_PORT
                        + ": expected exactly one LockSupport::parkNanos");
            }
            if (auditedPaths.contains(INTERRUPT_PORT)
                    && (interruptReads != 1 || interruptRestores != 1)) {
                throw new AssertionError(INTERRUPT_PORT
                        + ": expected exactly one interrupt read and restoration");
            }
        }
    }

    private static Map<Path, String> productionSources(Path root)
            throws IOException {
        Map<Path, String> sources = new java.util.LinkedHashMap<>();
        try (var paths = walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java"))
                    .sorted().toList()) {
                sources.put(path, readString(path));
            }
        }
        return sources;
    }

}
