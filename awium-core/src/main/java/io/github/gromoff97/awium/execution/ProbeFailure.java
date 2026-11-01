package io.github.gromoff97.awium.execution;

import static java.util.Objects.requireNonNull;

public final class ProbeFailure extends RuntimeException {

    private final String location;

    private ProbeFailure(String location, Throwable cause) {
        super(null, requireNonNull(cause, "cause must not be null"), false, false);
        this.location = location;
    }

    public static ProbeFailure locatedFailure(String location, Throwable cause) {
        return cause instanceof ProbeFailure nested
                ? new ProbeFailure(location + " → " + nested.location, nested.getCause())
                : new ProbeFailure(location, cause);
    }

    public String location() {
        return location;
    }
}
