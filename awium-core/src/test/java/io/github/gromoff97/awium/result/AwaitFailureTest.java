package io.github.gromoff97.awium.result;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitFailureTest {
    @Test
    void copiesSuppressedCauses() {
        var original = new AssertionError("original");
        var causes = new ArrayList<Throwable>(List.of(original));
        var failure = new AwaitFailure(TIMEOUT, "not ready", null, causes);
        causes.clear();
        assertEquals(List.of(original), failure.suppressed());
        assertThrows(UnsupportedOperationException.class, () -> failure.suppressed().clear());
    }

    @Test
    void allowsNoCause() {
        assertNull(new AwaitFailure(TIMEOUT, "not ready", null, List.of()).cause());
    }

    @ParameterizedTest
    @CsvSource({", message", "TIMEOUT,"})
    void rejectsMissingRequiredFields(Reason reason, String message) {
        assertThrows(NullPointerException.class, () -> new AwaitFailure(reason, message, null, List.of()));
    }
}
