package io.github.gromoff97.awium;

import static java.nio.file.Files.readString;
import static java.nio.file.Files.walk;
import static java.util.Collections.newSetFromMap;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.openrewrite.Parser.Input.fromString;
import static org.openrewrite.java.JavaParser.fromJavaVersion;
import static org.openrewrite.java.search.FindMissingTypes.findMissingTypes;
import static org.openrewrite.java.tree.TypeUtils.asFullyQualified;
import static org.openrewrite.java.tree.TypeUtils.isAssignableTo;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;
import static org.openrewrite.java.tree.TypeUtils.isWellFormedType;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMissingTypes.MissingTypeResult;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.tree.ParseError;
import org.junit.jupiter.api.Test;

class ArchitectureContractTest {

    private static final Path MAIN_PACKAGE = Path.of("src", "main", "java", "io",
            "github", "gromoff97", "awium");

    @Test
    void productionSourcesUseOnlyApprovedWaitingAndInterruptionMechanics()
            throws IOException {
        Map<Path, String> sources = new java.util.LinkedHashMap<>();
        try (var paths = walk(MAIN_PACKAGE)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java"))
                    .toList()) {
                sources.put(path, readString(path));
            }
        }
        assertApprovedSources(sources);
    }

    @Test
    void architectureAuditAllowsUnrelatedNamesCommentsAndStrings() {
        assertApprovedSources(Map.of(
                Path.of("fixture", "Allowed.java"), """
                        package fixture;
                        final class Door { void lock() {} }
                        final class Timer { void schedule() {} }
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
                        """));
    }

    @Test
    void architectureAuditFailsOnParserAndTypeErrors() {
        for (Map<Path, String> invalid : List.of(
                Map.of(Path.of("Syntax.java"), "final class Syntax {"),
                Map.of(Path.of("Types.java"),
                        "final class Types { MissingType value; }"))) {
            assertThrows(AssertionError.class,
                    () -> assertApprovedSources(invalid));
        }
    }

    @Test
    void architectureAuditRejectsForbiddenResolvedExecutableTypes() {
        ofEntries(
                entry("implicit future return", """
                        class Mutant {
                            void run(java.net.http.HttpClient client,
                                    java.net.http.HttpRequest request) {
                                client.sendAsync(request,
                                        java.net.http.HttpResponse.BodyHandlers.ofString());
                            }
                        }
                        """),
                entry("nested generic array return", """
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
                        """))
                .forEach(ArchitectureContractTest::assertRejected);
    }

    @Test
    void architectureAuditRejectsEveryApprovedBan() {
        Map<String, String> mutants = ofEntries(
                entry("thread subclass", "class Mutant extends Thread {}"),
                entry("worker start reference",
                        "class Mutant { java.util.function.Consumer<Thread> start = Thread::start; }"),
                entry("thread construction",
                        "class Mutant { Thread worker = new Thread(); }"),
                entry("virtual thread builder",
                        "class Mutant { Thread.Builder.OfVirtual builder = Thread.ofVirtual(); }"),
                entry("sleep",
                        "class Mutant { void run() throws InterruptedException { Thread.sleep(1); } }"),
                entry("static-imported executor factory", """
                        import static java.util.concurrent.Executors.newFixedThreadPool;
                        class Mutant { Object worker = newFixedThreadPool(1); }
                        """),
                entry("interrupt read reference",
                        "class Mutant { java.util.function.BooleanSupplier read = Thread::interrupted; }"),
                entry("monitor method",
                        "class Mutant { void run(Object lock) throws InterruptedException { lock.wait(); } }"),
                entry("synchronized monitor",
                        "class Mutant { synchronized void run() {} }"),
                entry("synchronized block", """
                        class Mutant {
                            void run(Object monitor) {
                                synchronized (monitor) {}
                            }
                        }
                        """),
                entry("atomic",
                        "class Mutant { java.util.concurrent.atomic.AtomicInteger state; }"),
                entry("var-handle atomic",
                        "class Mutant { java.lang.invoke.VarHandle state; }"),
                entry("lock",
                        "class Mutant { java.util.concurrent.locks.Lock lock; }"),
                entry("LockSupport outside the approved park port",
                        "class Mutant { void run() { java.util.concurrent.locks.LockSupport.park(); } }"),
                entry("interrupt read",
                        "class Mutant { boolean read(Thread t) { return t.isInterrupted(); } }"),
                entry("interrupt restore",
                        "class Mutant { void restore(Thread t) { t.interrupt(); } }"),
                entry("future type",
                        "class Mutant { java.util.concurrent.Future<?> future; }"),
                entry("timer root",
                        "class Mutant { java.util.Timer timer; }"));

        mutants.forEach(ArchitectureContractTest::assertRejected);
    }

    @Test
    void architectureAuditRejectsMutatedApprovedPorts() {
        assertRejectedAt(MAIN_PACKAGE.resolve(
                "await/AbstractAwait.java"), """
                package io.github.gromoff97.awium.await;
                abstract class AbstractAwait {}
                """);
        assertRejectedAt(MAIN_PACKAGE.resolve(
                "await/AbstractAwait.java"), """
                package io.github.gromoff97.awium.await;
                import java.util.concurrent.locks.LockSupport;
                abstract class AbstractAwait {
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
        assertRejectedAt(MAIN_PACKAGE.resolve(
                "engine/ObservationEvaluator.java"), """
                package io.github.gromoff97.awium.engine;
                final class ObservationEvaluator {
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

    @Test
    void engineAndDiagnosticsDoNotDependOnAwaitFacade() {
        for (String lowerPackage : List.of("engine", "diagnostics")) {
            Map<Path, String> invertedDependency = Map.of(
                    MAIN_PACKAGE.resolve("await/Facade.java"), """
                            package io.github.gromoff97.awium.await;
                            public final class Facade {}
                            """,
                    MAIN_PACKAGE.resolve(lowerPackage + "/Mutant.java"), """
                            package io.github.gromoff97.awium.%s;
                            import io.github.gromoff97.awium.await.Facade;
                            final class Mutant { Facade facade; }
                            """.formatted(lowerPackage));
            AssertionError rejection = assertThrows(AssertionError.class,
                    () -> assertApprovedSources(invertedDependency), lowerPackage);
            assertFalse(rejection.getMessage().contains("OpenRewrite parsing failed"),
                    lowerPackage + " dependency must be rejected semantically");
            assertFalse(rejection.getMessage().contains("type attribution failed"),
                    lowerPackage + " dependency must be rejected semantically");
        }
    }

    @Test
    void lowerLayersCannotBypassDependencyRuleWithQualifiedNames() {
        Map<Path, String> invertedDependency = Map.of(
                MAIN_PACKAGE.resolve("await/Facade.java"), """
                        package io.github.gromoff97.awium.await;
                        public final class Facade {}
                        """,
                MAIN_PACKAGE.resolve("engine/Mutant.java"), """
                        package io.github.gromoff97.awium.engine;
                        final class Mutant {
                            io.github.gromoff97.awium.await.Facade facade;
                        }
                        """);
        assertThrows(AssertionError.class,
                () -> assertApprovedSources(invertedDependency));
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
        List<SourceFile> parsed = fromJavaVersion().build().parseInputs(
                sources.entrySet().stream()
                        .map(entry -> fromString(
                                entry.getKey(), entry.getValue()))
                        .toList(),
                Path.of(""),
                context).toList();
        parsed.stream().filter(ParseError.class::isInstance)
                .map(ParseError.class::cast).findFirst().ifPresent(error -> {
                    throw new AssertionError(error.getSourcePath()
                            + ": OpenRewrite parsing failed", error.toException());
                });

        ArchitectureVisitor visitor = new ArchitectureVisitor();
        for (SourceFile source : parsed) {
            J.CompilationUnit unit = (J.CompilationUnit) source;
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
                        "await/AbstractAwait.java").normalize();
        private static final Path WAIT_INTERRUPT_PORT = MAIN_PACKAGE
                .resolve("engine/WaitEngine.java").normalize();
        private static final Path OBSERVATION_INTERRUPT_PORT = MAIN_PACKAGE
                .resolve("engine/ObservationEvaluator.java").normalize();
        private static final Path FINAL_INTERRUPT_PORT = MAIN_PACKAGE
                .resolve("diagnostics/FailureFactory.java").normalize();
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
        private int waitInterruptReads;
        private int waitInterruptRestores;
        private int observationInterruptReads;
        private int observationInterruptRestores;
        private int finalInterruptReads;
        private int finalInterruptRestores;

        @Override
        public J.CompilationUnit visitCompilationUnit(
                J.CompilationUnit unit, ExecutionContext context) {
            auditedPaths.add(unit.getSourcePath().normalize());
            return super.visitCompilationUnit(unit, context);
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration declaration, ExecutionContext context) {
            if (isAssignableTo(THREAD, declaration.getType())) {
                reject("Thread subclass");
            }
            return super.visitClassDeclaration(declaration, context);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(
                J.MethodDeclaration declaration, ExecutionContext context) {
            if (declaration.getMethodType().hasFlags(Flag.Synchronized)) {
                reject("synchronized method");
            }
            return super.visitMethodDeclaration(declaration, context);
        }

        @Override
        public J.Synchronized visitSynchronized(
                J.Synchronized synchronizedBlock, ExecutionContext context) {
            reject("synchronized block");
            return synchronizedBlock;
        }

        @Override
        public J.NewClass visitNewClass(
                J.NewClass construction, ExecutionContext context) {
            JavaType.Method constructor = construction.getConstructorType();
            checkExecutable(construction, constructor);
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
            checkTypeReference(identifier.getType());
            return super.visitIdentifier(identifier, context);
        }

        @Override
        public J.Import visitImport(J.Import imported, ExecutionContext context) {
            JavaType type = imported.isStatic()
                    ? imported.getQualid().getTarget().getType()
                    : imported.getQualid().getType();
            if (type == null || !isWellFormedType(type)) {
                reject("OpenRewrite type attribution failed for import");
            }
            rejectInvertedFacadeDependency(type);
            return super.visitImport(imported, context);
        }

        private void rejectInvertedFacadeDependency(JavaType type) {
            JavaType.FullyQualified dependency = asFullyQualified(type);
            if (dependency == null) {
                return;
            }
            Path source = currentPath();
            boolean lowerLayer = source.startsWith(MAIN_PACKAGE.resolve("engine"))
                    || source.startsWith(MAIN_PACKAGE.resolve("diagnostics"));
            if (lowerLayer && dependency.getFullyQualifiedName()
                    .startsWith("io.github.gromoff97.awium.await.")) {
                reject("lower layer depends on await facade");
            }
        }

        private void checkTypeReference(JavaType type) {
            if (type != null && isWellFormedType(type)) {
                rejectInvertedFacadeDependency(type);
                if (isOfClassType(type, LOCK_SUPPORT)) {
                    if (!isApprovedLockSupportReference()) {
                        reject("LockSupport outside the approved parkNanos port");
                    }
                } else if (hasForbiddenType(type, newSetFromMap(
                        new IdentityHashMap<>()))) {
                    reject("forbidden concurrency type reference");
                }
            }
        }

        private void checkExecutable(J tree, JavaType.Method method) {
            checkSignature(method);
            JavaType.FullyQualified owner = method.getDeclaringType();
            String name = method.getName();

            if (LOCK_SUPPORT_METHOD.matches(method)) {
                if (tree instanceof J.MemberReference
                        && PARK_NANOS.matches(method)
                        && currentPath().equals(PARK_PORT)) {
                    parks++;
                    return;
                }
                reject("LockSupport outside the approved parkNanos port");
            }
            if (THREAD_METHOD.matches(method)
                    && INTERRUPT_METHODS.contains(name)) {
                if (tree instanceof J.MethodInvocation invocation
                        && isApprovedInterruptCall(invocation, name)) {
                    if (name.equals("isInterrupted")) {
                        if (currentPath().equals(WAIT_INTERRUPT_PORT)) {
                            waitInterruptReads++;
                        } else if (currentPath().equals(OBSERVATION_INTERRUPT_PORT)) {
                            observationInterruptReads++;
                        } else {
                            finalInterruptReads++;
                        }
                    } else if (currentPath().equals(WAIT_INTERRUPT_PORT)) {
                        waitInterruptRestores++;
                    } else if (currentPath().equals(OBSERVATION_INTERRUPT_PORT)) {
                        observationInterruptRestores++;
                    } else {
                        finalInterruptRestores++;
                    }
                    return;
                }
                reject("Thread interrupt access outside approved ports");
            }
            if (THREAD_METHOD.matches(method)
                    && THREAD_WORK_METHODS.contains(name)) {
                reject("Thread worker mechanism " + name);
            }
            if (OBJECT_METHOD.matches(method)
                    && MONITOR_METHODS.contains(name)) {
                reject("Object monitor method " + name);
            }
            if (method.isConstructor()
                    && isAssignableTo(THREAD, owner)) {
                reject("Thread construction");
            }
            if (isForbiddenMechanism(owner)) {
                reject("concurrency mechanism "
                        + owner.getFullyQualifiedName() + "." + name);
            }
        }

        private void checkSignature(JavaType.Method method) {
            Set<JavaType> visited = newSetFromMap(
                    new IdentityHashMap<>());
            if (hasForbiddenType(method.getReturnType(), visited)
                    || method.getParameterTypes().stream()
                    .anyMatch(type -> hasForbiddenType(type, visited))) {
                reject("forbidden concurrency type in executable "
                        + method.getDeclaringType().getFullyQualifiedName()
                        + "." + method.getName());
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
                    && PARK_NANOS.matches(reference.getMethodType());
        }

        private boolean isApprovedInterruptCall(
                J.MethodInvocation invocation, String name) {
            boolean approved = currentPath().equals(WAIT_INTERRUPT_PORT)
                    && (name.equals("isInterrupted") || name.equals("interrupt"))
                    || currentPath().equals(OBSERVATION_INTERRUPT_PORT)
                    && (name.equals("isInterrupted") || name.equals("interrupt"))
                    || currentPath().equals(FINAL_INTERRUPT_PORT)
                    && (name.equals("isInterrupted")
                            || name.equals("interrupt"));
            return approved
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

        private Path currentPath() {
            return getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                    .getSourcePath().normalize();
        }

        private void reject(String reason) {
            throw new AssertionError(currentPath() + ": " + reason);
        }

        private void verifyApprovedOccurrences() {
            if (auditedPaths.contains(PARK_PORT) && parks != 1) {
                throw new AssertionError(PARK_PORT
                        + ": expected exactly one LockSupport::parkNanos");
            }
            if (auditedPaths.contains(WAIT_INTERRUPT_PORT)
                    && (waitInterruptReads != 1 || waitInterruptRestores != 1)) {
                throw new AssertionError(WAIT_INTERRUPT_PORT
                        + ": expected exactly one interrupt read and restoration");
            }
            if (auditedPaths.contains(OBSERVATION_INTERRUPT_PORT)
                    && (observationInterruptReads != 1
                            || observationInterruptRestores != 1)) {
                throw new AssertionError(OBSERVATION_INTERRUPT_PORT
                        + ": expected exactly one interrupt read and restoration");
            }
            if (auditedPaths.contains(FINAL_INTERRUPT_PORT)
                    && (finalInterruptReads != 1
                            || finalInterruptRestores != 1)) {
                throw new AssertionError(FINAL_INTERRUPT_PORT
                        + ": expected exactly one final interrupt read and restoration");
            }
        }
    }
}
