package io.github.gromoff97.assertility;

final class Diagnostics {
    private Diagnostics() {
    }

    static AwaitFailure awaitFailure(
            AwaitSpec<?> spec, String terminalName, RuntimeException engineFailure) {
        var message = new StringBuilder("Await failed");
        if (spec.description() != null) {
            message.append(System.lineSeparator())
                    .append("Description: ")
                    .append(spec.description());
        }
        message.append(System.lineSeparator())
                .append("Terminal: ")
                .append(terminalName);
        if (engineFailure.getMessage() != null) {
            message.append(System.lineSeparator())
                    .append("Awaitility: ")
                    .append(engineFailure.getMessage());
        }
        return new AwaitFailure(message.toString(), engineFailure);
    }
}
