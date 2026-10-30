package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionTestRuntime.assertUnsatisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectAndOptionalConditionsTest {

    @Test
    void nullabilityConditionsAreExactPositiveAndNegativePairs() {
        var actual = new Object();

        assertNull(result(evaluate(isNull, null)));
        assertUnsatisfied(evaluate(isNull, actual));
        assertSame(actual, result(evaluate(isNotNull, actual)));
        assertUnsatisfied(evaluate(isNotNull, null));
    }

    @Test
    void equalityAndIdentityConditionsAreComplementsAndReturnTheActualSnapshot() {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";

        String selected = result(evaluate(equalTo(expected), equalActual));
        assertSame(equalActual, selected);
        assertUnsatisfied(evaluate(notEqualTo(expected), equalActual));
        assertUnsatisfied(evaluate(equalTo(expected), differentActual));
        assertSame(differentActual, result(evaluate(notEqualTo(expected), differentActual)));
        assertSame(equalActual, result(evaluate(sameAs(equalActual), equalActual)));
        assertUnsatisfied(evaluate(sameAs(expected), equalActual));
        assertSame(equalActual, result(evaluate(notSameAs(expected), equalActual)));
        assertUnsatisfied(evaluate(notSameAs(equalActual), equalActual));
        assertNull(result(evaluate(equalTo(null), null)));
        AttemptResult<?> arrays = evaluate(equalTo(
                new int[]{1, 2}), new int[]{1, 2});
        assertInstanceOf(Satisfied.class, arrays);
        assertEquals(int[].class, result(arrays).getClass());
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent() {
        Optional<String> empty = Optional.empty();
        Optional<String> presentValue = Optional.of("value");
        var time = new FakeTime(0);
        var missing = await(() -> (Optional<String>) null).usingTime(time, time).upTo(ofNanos(1));
        var emptySource = await(() -> empty).usingTime(time, time).upTo(ofNanos(1));
        var presentSource = await(() -> presentValue).usingTime(time, time).upTo(ofNanos(1));

        assertThat(present.description()).isNotBlank();
        assertFailure(TIMEOUT, () -> missing.until(present));
        assertFailure(TIMEOUT, () -> missing.until(absent));
        assertFailure(TIMEOUT, () -> emptySource.until(present));
        assertSame(empty, emptySource.until(absent));
        String selected = presentSource.until(present);
        assertSame("value", selected);
        assertFailure(TIMEOUT, () -> presentSource.until(absent));
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue() {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";
        var equal = hasValue(equalTo(expected));
        var notEqual = hasValue(notEqualTo(expected));
        var time = new FakeTime(0);
        var equalSource = await(() -> Optional.of(equalActual)).usingTime(time, time).upTo(ofNanos(1));
        var differentSource = await(() -> Optional.of(differentActual)).usingTime(time, time).upTo(ofNanos(1));

        assertFailure(TIMEOUT, () -> await(() -> (Optional<String>) null)
                .usingTime(time, time).upTo(ofNanos(1)).until(equal));
        Optional<String> empty = Optional.empty();
        assertFailure(TIMEOUT, () -> await(() -> empty).usingTime(time, time).upTo(ofNanos(1)).until(equal));
        String selectedEqual = equalSource.until(equal);
        assertSame(equalActual, selectedEqual);
        assertFailure(TIMEOUT, () -> equalSource.until(notEqual));
        assertFailure(TIMEOUT, () -> differentSource.until(equal));
        String selectedDifferent = differentSource.until(notEqual);
        assertSame(differentActual, selectedDifferent);
    }

    @Test
    void optionalValueEqualityUsesActualFirstAndArrayContent() {
        var expected = new Directional(false);
        var actual = new Directional(true);

        Directional selected = await(() -> Optional.of(actual)).until(hasValue(equalTo(expected)));
        assertSame(actual, selected);
        assertEquals(1, actual.equalsCalls);

        int[] actualArray = {1, 2};
        int[] selectedArray = await(() -> Optional.of(actualArray)).until(hasValue(equalTo(new int[]{1, 2})));
        assertSame(actualArray, selectedArray);
    }

    @Test
    void optionalValueFactoriesRejectNullConditionsImmediately() {
        assertThatNullPointerException().isThrownBy(() -> hasValue((Condition.Typed<Object, Object>) null))
                .withMessageContaining("condition");
        assertThatNullPointerException().isThrownBy(() -> hasValue((Condition.PreservingCondition<Object>) null))
                .withMessageContaining("condition");
    }
}
