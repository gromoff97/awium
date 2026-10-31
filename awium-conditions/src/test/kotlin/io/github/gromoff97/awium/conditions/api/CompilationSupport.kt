package io.github.gromoff97.awium.conditions.api

import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects.forSourceString
import org.assertj.core.api.Assertions.assertThat

internal object CompilationSupport {
    private val typeErrors = setOf(
        "compiler.err.cant.apply.symbol", "compiler.err.cant.apply.symbols", "compiler.err.inconvertible.types",
        "compiler.err.prob.found.req", "compiler.err.ref.ambiguous", "compiler.err.type.found.req"
    )

    fun assertRejected(source: String) {
        val compilation = javac().withOptions("--release", "21", "-Xlint:all", "-Werror", "-proc:none")
            .compile(forSourceString("Contract", source))
        assertThat(compilation.errors().map { it.code }).`as`("fixture must fail only for incompatible types:%n%s", source)
            .isNotEmpty().isSubsetOf(typeErrors)
    }
}
