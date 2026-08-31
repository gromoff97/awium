package io.github.gromoff97.awium;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.writeString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

public final class CompilationSupport {

    private static final Set<String> TYPE_REJECTION_CODES = Set.of(
            "compiler.err.cant.apply.symbol",
            "compiler.err.cant.apply.symbols",
            "compiler.err.inconvertible.types",
            "compiler.err.prob.found.req",
            "compiler.err.ref.ambiguous",
            "compiler.err.type.found.req");
    private static final Set<String> MISSING_METHOD_CODES = Set.of(
            "compiler.err.cant.resolve.location",
            "compiler.err.cant.resolve.location.args");

    public static boolean compiles(Path directory, String source) throws IOException {
        return compiles(directory, source,
                Path.of(System.getProperty("java.class.path")));
    }

    public static boolean compiles(Path directory, String source,
            String expectedMissingMethod) throws IOException {
        return compiles(directory, source,
                Path.of(System.getProperty("java.class.path")), expectedMissingMethod);
    }

    public static boolean compiles(Path directory, String source, Path classpath)
            throws IOException {
        return compiles(directory, source, classpath, null);
    }

    private static boolean compiles(Path directory, String source, Path classpath,
            String expectedMissingMethod) throws IOException {
        Path sourceFile = directory.resolve("Contract.java");
        writeString(sourceFile, source);
        return compile(List.of("--release", "21", "-Xlint:all",
                        "-Werror", "-proc:none", "-classpath",
                        classpath.toString(), "-d", directory.toString()),
                expectedMissingMethod, sourceFile);
    }

    public static boolean compilesModule(Path directory, String descriptor,
            String source, Path modulePath) throws IOException {
        Path moduleFile = directory.resolve("module-info.java");
        Path sourceFile = directory.resolve("Contract.java");
        writeString(moduleFile, descriptor);
        writeString(sourceFile, source);
        return compile(List.of("--release", "21", "-Xlint:all", "-Werror",
                        "-proc:none", "--module-path", modulePath.toString(),
                        "-d", directory.toString()), null, moduleFile, sourceFile);
    }

    private static boolean compile(List<String> options, String expectedMissingMethod,
            Path... sourceFiles) throws IOException {
        var compiler = requireNonNull(getSystemJavaCompiler(), "system compiler unavailable");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            boolean compiled = Boolean.TRUE.equals(compiler.getTask(null, files,
                    diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(List.of(sourceFiles))).call());
            if (!compiled) {
                requireTypeRejection(diagnostics.getDiagnostics(), expectedMissingMethod);
            }
            return compiled;
        }
    }

    private static void requireTypeRejection(List<Diagnostic<? extends JavaFileObject>> diagnostics,
            String expectedMissingMethod) {
        List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == ERROR)
                .toList();
        if (errors.isEmpty() || errors.stream()
                .anyMatch(error -> !isExpectedRejection(error, expectedMissingMethod))) {
            String details = diagnostics.stream()
                    .map(diagnostic -> diagnostic.getCode() + ": "
                            + diagnostic.getMessage(Locale.ROOT))
                    .collect(joining("\n"));
            throw new AssertionError("fixture failed for an unrelated reason:\n" + details);
        }
    }

    private static boolean isExpectedRejection(Diagnostic<? extends JavaFileObject> error,
            String expectedMissingMethod) {
        return TYPE_REJECTION_CODES.contains(error.getCode())
                || expectedMissingMethod != null
                && MISSING_METHOD_CODES.contains(error.getCode())
                && error.getMessage(Locale.ROOT).contains("method " + expectedMissingMethod + "(");
    }
}
