package io.github.gromoff97.awium;

import java.util.Set;
import javax.tools.Diagnostic;

import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaFileObjects.forSourceString;
import static org.assertj.core.api.Assertions.assertThat;

final class CompilationSupport {

    private static final Set<String> TYPE_REJECTION_CODES = Set.of(
            "compiler.err.cant.apply.symbol",
            "compiler.err.cant.apply.symbols",
            "compiler.err.inconvertible.types",
            "compiler.err.prob.found.req",
            "compiler.err.ref.ambiguous",
            "compiler.err.type.found.req");

    static void assertRejected(String source) {
        var compilation = javac().withOptions("--release", "21", "-Xlint:all", "-Werror", "-proc:none")
                .compile(forSourceString("Contract", source));
        assertThat(compilation.errors()).as("fixture must fail only for incompatible types:%n%s", source)
                .isNotEmpty().extracting(Diagnostic::getCode).isSubsetOf(TYPE_REJECTION_CODES);
    }
}
