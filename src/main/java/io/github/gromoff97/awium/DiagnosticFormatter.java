package io.github.gromoff97.awium;

@FunctionalInterface
interface DiagnosticFormatter {

    String format(FailureContext<?> context);
}
