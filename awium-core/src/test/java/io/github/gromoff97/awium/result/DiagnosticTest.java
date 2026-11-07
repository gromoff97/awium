package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition.Reference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticTest {

    @Test
    void fieldsAreAnImmutableCopyOfTheProvidedList() {
        var field = new Reference<>("Actual size", 2);
        var fields = new ArrayList<Reference<?>>(List.of(field));
        var diagnostic = new Diagnostic(null, fields);
        fields.clear();

        assertEquals(List.of(field), diagnostic.fields());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.fields().clear());
    }

    @Test
    void changingAMessageKeepsTheFieldsAndLeavesTheOriginalUnchanged() {
        var original = new Diagnostic("not ready", new Reference<>("Status", "pending"));
        var nested = original.withMismatch("user: not ready");

        assertEquals("not ready", original.mismatch());
        assertEquals("user: not ready", nested.mismatch());
        assertEquals(original.fields(), nested.fields());
    }
}
