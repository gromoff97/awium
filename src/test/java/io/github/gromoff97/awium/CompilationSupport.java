package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.writeString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

final class CompilationSupport {

    private static final Set<String> TYPE_REJECTION_CODES = Set.of(
            "compiler.err.cant.apply.symbol",
            "compiler.err.cant.apply.symbols",
            "compiler.err.inconvertible.types",
            "compiler.err.prob.found.req",
            "compiler.err.ref.ambiguous",
            "compiler.err.type.found.req");

    static void assertRejected(Path directory, String source) throws IOException {
        Path sourceFile = directory.resolve("Contract.java");
        writeString(sourceFile, source);
        var options = List.of("--release", "21", "-Xlint:all",
                "-Werror", "-proc:none", "-classpath",
                System.getProperty("java.class.path"), "-d", directory.toString());
        var compiler = requireNonNull(getSystemJavaCompiler(), "system compiler unavailable");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, UTF_8)) {
            boolean compiled = Boolean.TRUE.equals(compiler.getTask(null, files,
                    diagnostics, options, null,
                    files.getJavaFileObjects(sourceFile)).call());
            if (compiled) {
                throw new AssertionError("fixture unexpectedly compiled:\n" + source);
            }
            requireExpectedRejection(diagnostics.getDiagnostics());
        }
    }

    private static void requireExpectedRejection(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == ERROR)
                .toList();
        if (errors.isEmpty() || errors.stream()
                .anyMatch(error -> !TYPE_REJECTION_CODES.contains(error.getCode()))) {
            String details = diagnostics.stream()
                    .map(diagnostic -> diagnostic.getCode() + ": "
                            + diagnostic.getMessage(Locale.ROOT))
                    .collect(joining("\n"));
            throw new AssertionError("fixture failed for an unrelated reason:\n" + details);
        }
    }

}
