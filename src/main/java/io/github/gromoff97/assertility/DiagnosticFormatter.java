package io.github.gromoff97.assertility;

@FunctionalInterface
interface DiagnosticFormatter {

    String format(FailureContext<?> context);
}
