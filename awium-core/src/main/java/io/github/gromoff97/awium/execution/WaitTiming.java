package io.github.gromoff97.awium.execution;

public interface WaitTiming {
    long everyNanos();

    long upToNanos();

    long persistenceNanos();
}
