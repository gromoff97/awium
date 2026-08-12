package io.github.gromoff97.awium;

import static java.io.OutputStream.nullOutputStream;
import static java.nio.file.Files.writeString;
import static java.util.Objects.requireNonNull;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.nio.file.Path;

final class CompilationSupport {

    static boolean compiles(Path directory, String source) throws IOException {
        return compiles(directory, source,
                Path.of(System.getProperty("java.class.path")));
    }

    static boolean compiles(Path directory, String source, Path classpath)
            throws IOException {
        Path sourceFile = directory.resolve("Contract.java");
        writeString(sourceFile, source);
        return requireNonNull(getSystemJavaCompiler(),
                "system compiler unavailable").run(null, nullOutputStream(),
                nullOutputStream(), "--release", "21", "-Xlint:all",
                "-Werror", "-proc:none", "-classpath",
                classpath.toString(), "-d", directory.toString(),
                sourceFile.toString()) == 0;
    }
}
