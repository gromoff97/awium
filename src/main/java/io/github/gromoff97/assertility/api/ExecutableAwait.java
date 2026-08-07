package io.github.gromoff97.assertility.api;

public interface ExecutableAwait extends ExecutableTerminals {
    ExecutableTerminals as(String description);

    ExecutableTerminals as(String format, Object... args);
}
