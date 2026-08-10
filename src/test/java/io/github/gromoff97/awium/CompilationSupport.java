package io.github.gromoff97.awium;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

final class CompilationSupport {

    private CompilationSupport() {
    }

    static boolean compiles(Path parent, String source) throws IOException {
        return compiles(parent, source,
                Path.of(System.getProperty("java.class.path")));
    }

    static boolean compiles(Path parent, String source, Path classpath)
            throws IOException {
        Path directory = Files.createTempDirectory(parent, "javac-");
        Path sourceFile = directory.resolve("Contract.java");
        Files.writeString(sourceFile, source);
        JavaCompiler compiler = Objects.requireNonNull(
                ToolProvider.getSystemJavaCompiler(), "system compiler unavailable");
        return compiler.run(null, OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream(), "--release", "21", "-Xlint:all",
                "-Werror", "-proc:none", "-classpath",
                classpath.toString(), "-d", directory.toString(),
                sourceFile.toString()) == 0;
    }
}
