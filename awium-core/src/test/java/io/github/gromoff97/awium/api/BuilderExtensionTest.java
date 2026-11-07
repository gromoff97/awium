package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.DiagnosticAwait;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.execution.DefaultAwaitHandler;
import io.github.gromoff97.awium.support.FakeTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuilderExtensionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void extensionsRetainSourceTimingAndHandlerFactory(boolean diagnostic) {
        var time = new FakeTime(0);
        int[] calls = {0};
        int[] handlers = {0};
        var original = timedAwait(() -> ++calls[0] == 1 ? "pending" : "ready", time)
                .every(ofNanos(2)).upTo(ofNanos(3)).persisting(ofNanos(4))
                .handling(() -> { handlers[0]++; return new DefaultAwaitHandler<>(); });

        String selected;
        if (diagnostic) selected = new CopiedDiagnostic<>(diagnostic(original)).until(expected("ready")).orThrow();
        else selected = new CopiedValue<>(original).until(expected("ready"));

        assertEquals("ready", selected);
        assertEquals(6L, time.getAsLong());
        assertEquals(4, calls[0]);
        assertEquals(1, handlers[0]);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anExtensionRequiresAnExistingBuilder(boolean diagnostic) {
        var failure = assertThrows(NullPointerException.class, () -> {
            if (diagnostic) new CopiedDiagnostic<>(null);
            else new CopiedValue<>(null);
        });
        assertEquals("waiting must not be null", failure.getMessage());
    }

    private static final class CopiedValue<OBSERVED> extends ValueAwait<OBSERVED> {
        private CopiedValue(ValueAwait<OBSERVED> original) { super(original); }
    }

    private static final class CopiedDiagnostic<OBSERVED> extends DiagnosticAwait<OBSERVED> {
        private CopiedDiagnostic(DiagnosticAwait<OBSERVED> original) { super(original); }
    }
}
