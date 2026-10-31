package io.github.gromoff97.awium.function;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.function.CheckedPredicate.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckedPredicateTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void negationCallsThePredicateOnceWithTheOriginalValue(boolean answer) throws Throwable {
        CheckedPredicate<Object> predicate = mock();
        var actual = new Object();
        when(predicate.test(actual)).thenReturn(answer);

        assertEquals(!answer, not(predicate).test(actual));
        verify(predicate).test(actual);
    }

    @Test
    void negationPassesNullToThePredicate() throws Throwable {
        CheckedPredicate<Object> predicate = mock();

        not(predicate).test(null);

        verify(predicate).test(null);
    }

    @Test
    void negationRejectsAMissingPredicate() {
        assertThrows(NullPointerException.class, () -> not(null));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void negationPropagatesTheOriginalFailure(Throwable cause) {
        var predicate = not(value -> { throw cause; });

        assertSame(cause, assertThrows(Throwable.class, () -> predicate.test("value")));
    }

    private static Stream<Throwable> failures() {
        return Stream.of(new IOException(), new AssertionError(), new Throwable(), new InterruptedException());
    }
}
