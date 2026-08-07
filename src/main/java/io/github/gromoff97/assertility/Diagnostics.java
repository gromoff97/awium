package io.github.gromoff97.assertility;

import org.assertj.core.configuration.ConfigurationProvider;

import java.util.Collection;
import java.util.List;

final class Diagnostics {
    private Diagnostics() {
    }

    static AwaitFailure awaitFailure(
            AwaitSpec<?> spec, String terminalName, RuntimeException engineFailure) {
        var message = new StringBuilder();
        if (spec.description() != null) {
            message.append(spec.description()).append(": ");
        }
        message.append(terminalName).append(" did not complete: ")
                .append(engineFailure.getMessage() == null
                        ? engineFailure.getClass().getSimpleName()
                        : engineFailure.getMessage());
        return new AwaitFailure(message.toString(), engineFailure);
    }

    static AssertionError selectorFailure(
            String selector,
            Collection<?> source,
            CollectionSupport.Matches<?> matches,
            String expectedMatches,
            String description) {
        var message = new StringBuilder(selector)
                .append(" selector did not match")
                .append(System.lineSeparator())
                .append("source size: ").append(source.size())
                .append(System.lineSeparator())
                .append("expected matches: ").append(expectedMatches)
                .append(System.lineSeparator())
                .append("actual matches: ").append(matches.elements().size());
        if (description != null) {
            message.append(System.lineSeparator())
                    .append("description: ").append(description);
        }

        var relevant = matches.elements().isEmpty() ? matches.observed() : matches.elements();
        appendRenderedSample(message, relevant);
        appendComparisonFailures(message, matches.comparisonFailures());
        return new AssertionError(message.toString());
    }

    static AssertionError quantifierFailure(
            String quantifier,
            Collection<?> source,
            CollectionSupport.Matches<?> matches,
            String description) {
        var message = new StringBuilder(quantifier)
                .append(" quantifier did not match")
                .append(System.lineSeparator())
                .append("source size: ").append(source.size())
                .append(System.lineSeparator())
                .append("matching elements: ").append(matches.elements().size());
        if (description != null) {
            message.append(System.lineSeparator())
                    .append("description: ").append(description);
        }
        var relevant = "all".equals(quantifier)
                ? matches.nonMatches()
                : matches.elements();
        appendRenderedSample(message, relevant);
        appendComparisonFailures(message, matches.comparisonFailures());
        return new AssertionError(message.toString());
    }

    private static void appendRenderedSample(StringBuilder message, List<?> relevant) {
        var sampleSize = Math.min(10, relevant.size());
        message.append(System.lineSeparator()).append("sample elements:");
        var representation = ConfigurationProvider.CONFIGURATION_PROVIDER.representation();
        for (var index = 0; index < sampleSize; index++) {
            message.append(System.lineSeparator())
                    .append("- ")
                    .append(representation.toStringOf(relevant.get(index)));
        }
        message.append(System.lineSeparator())
                .append("omitted elements: ")
                .append(relevant.size() - sampleSize);
    }

    private static void appendComparisonFailures(
            StringBuilder message, List<AssertionError> failures) {
        if (failures.isEmpty()) {
            return;
        }
        var sampleSize = Math.min(3, failures.size());
        message.append(System.lineSeparator()).append("candidate comparison failures:");
        for (var index = 0; index < sampleSize; index++) {
            message.append(System.lineSeparator())
                    .append("--- candidate ").append(index + 1).append(" ---")
                    .append(System.lineSeparator())
                    .append(failures.get(index).getMessage());
        }
        message.append(System.lineSeparator())
                .append("omitted candidate comparisons: ")
                .append(failures.size() - sampleSize);
    }
}
