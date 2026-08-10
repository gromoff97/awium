package io.github.gromoff97.awium;

import static java.io.OutputStream.nullOutputStream;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.writeString;
import static java.util.Objects.requireNonNull;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.nio.file.Path;
import javax.tools.JavaCompiler;

final class CompilationSupport {

    private CompilationSupport() {
        throw new AssertionError("Utility class");
    }

    static boolean compiles(Path parent, String source) throws IOException {
        return compiles(parent, source,
                Path.of(System.getProperty("java.class.path")));
    }

    static boolean compiles(Path parent, String source, Path classpath)
            throws IOException {
        Path directory = createTempDirectory(parent, "javac-");
        Path sourceFile = directory.resolve("Contract.java");
        writeString(sourceFile, source);
        JavaCompiler compiler = requireNonNull(
                getSystemJavaCompiler(), "system compiler unavailable");
        return compiler.run(null, nullOutputStream(),
                nullOutputStream(), "--release", "21", "-Xlint:all",
                "-Werror", "-proc:none", "-classpath",
                classpath.toString(), "-d", directory.toString(),
                sourceFile.toString()) == 0;
    }
}
