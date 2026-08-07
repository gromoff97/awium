package io.github.gromoff97.assertility;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class CompilationSupport {
    private CompilationSupport() {
    }

    static CompilationResult compile(Path directory, String className, String source) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Tests require a JDK with the system Java compiler");
        }

        var sourceFile = directory.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        var classesDirectory = directory.resolve("classes");
        Files.createDirectories(classesDirectory);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = fileManager.getJavaFileObjects(sourceFile);
            var options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "--release", "21",
                    "-d", classesDirectory.toString());
            var succeeded = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            return new CompilationResult(succeeded ? 0 : 1, format(diagnostics));
        }
    }

    private static String format(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> "%s:%d:%d: %s".formatted(
                        diagnostic.getKind(),
                        diagnostic.getLineNumber(),
                        diagnostic.getColumnNumber(),
                        diagnostic.getMessage(Locale.ROOT)))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    record CompilationResult(int exitCode, String diagnostics) {
    }
}
