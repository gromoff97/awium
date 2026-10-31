package io.github.gromoff97.awium.function;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.function.CheckedBiPredicate.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckedBiPredicateTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void negationCallsThePredicateOnceWithBothOriginalValues(boolean answer) throws Throwable {
        CheckedBiPredicate<Object, Object> predicate = mock();
        var first = new Object();
        var second = new Object();
        when(predicate.test(first, second)).thenReturn(answer);

        assertEquals(!answer, not(predicate).test(first, second));
        verify(predicate).test(first, second);
    }

    @Test
    void negationPassesNullArgumentsToThePredicate() throws Throwable {
        CheckedBiPredicate<Object, Object> predicate = mock();

        not(predicate).test(null, null);

        verify(predicate).test(null, null);
    }

    @Test
    void negationRejectsAMissingPredicate() {
        assertThrows(NullPointerException.class, () -> not(null));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void negationPropagatesTheOriginalFailure(Throwable cause) {
        var predicate = not((first, second) -> { throw cause; });

        assertSame(cause, assertThrows(Throwable.class, () -> predicate.test("first", "second")));
    }

    private static Stream<Throwable> failures() {
        return Stream.of(new IOException(), new AssertionError(), new Throwable(), new InterruptedException());
    }
}
