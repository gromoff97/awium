package io.github.gromoff97.awium.result;

import org.junit.jupiter.api.Test;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptResultTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulResultsGuardTheirValueRenderer(boolean fatal) {
        var value = new Object() {
            @Override public String toString() {
                if (fatal) throw new AssertionError("value cannot be printed");
                throw new IllegalStateException("value cannot be printed");
            }
        };

        assertThat(satisfied(value).toString()).startsWith("Satisfied[value=<unprintable>, diagnostic=");
    }

    @Test
    void successfulResultsDisplayArrayContentsAndDiagnosticFields() {
        var diagnostic = new Diagnostic(null, new Reference<>("Size", 2));
        var value = new Object[]{new int[]{1, 2}, null};

        assertEquals("Satisfied[value=[[1, 2], null], diagnostic=Diagnostic[mismatch=null, fields=[Size=2]]]",
                satisfied(value, diagnostic).toString());
    }

    @Test
    void mappingAValuePreservesItsDiagnosticObject() {
        var diagnostic = new Diagnostic(null, new Reference<>("Length", 3));
        assertSame(diagnostic, satisfied("abc", diagnostic).mapValue(String::length).diagnostic());
    }

    @Test
    void mappingAMismatchPreservesItsDiagnosticObject() {
        var diagnostic = new Diagnostic("pending", new Reference<>("Progress", 2));
        assertSame(diagnostic, unsatisfied(diagnostic).mapValue(value -> "unused").diagnostic());
    }

    @Test
    void mapsSuccessfulValues() {
        assertEquals(satisfied(3), satisfied("abc").mapValue(String::length));
    }

    @Test
    void mapsNullAsASuccessfulValue() {
        assertEquals(satisfied("mapped"), satisfied(null).mapValue(value -> "mapped"));
    }

    @Test
    void mappingMayReturnNull() {
        assertEquals(satisfied(null), satisfied("abc").mapValue(value -> null));
    }

    @Test
    void mappingSkipsMismatchesAndPreservesTheirDiagnostics() {
        var cause = new IllegalStateException("not ready");
        var mismatch = AttemptResult.<String>unsatisfied("retry", cause).mapValue(value -> {
            throw new AssertionError("mismatches must not be mapped");
        });
        assertEquals(new Unsatisfied<>(new Diagnostic("retry"), cause), mismatch);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void unsatisfiedRequiresANonBlankMessage(String message) {
        var failure = assertThrows(IllegalArgumentException.class, () -> unsatisfied(message));
        assertEquals("unsatisfied message must not be null or blank", failure.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void diagnosticFieldsAndACauseDoNotReplaceTheRequiredMessage(String message) {
        var diagnostic = new Diagnostic(message, new Reference<>("Actual size", 2));
        var cause = new IllegalStateException("details");
        var failure = assertThrows(IllegalArgumentException.class, () -> new Unsatisfied<>(diagnostic, cause));
        assertEquals("unsatisfied message must not be null or blank", failure.getMessage());
    }

    @Test
    void unsatisfiedPreservesTheProvidedMessageAndCause() {
        var cause = new IllegalStateException("details");
        var detailed = unsatisfied("  not ready\ntry again  ", cause);
        assertEquals("  not ready\ntry again  ", detailed.mismatch());
        assertSame(cause, detailed.cause());
    }
}
