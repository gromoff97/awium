package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertUnsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.result;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
import static io.github.gromoff97.awium.conditions.ProbeContainers.Directional;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        ConditionResult<?> arrays = evaluate(equalTo(
                new int[]{1, 2}), new int[]{1, 2});
        assertInstanceOf(Satisfied.class, arrays);
        assertEquals(int[].class, result(arrays).getClass());
    }

    @Test
    void optionalPresenceConditionsDistinguishNullEmptyAndPresent() {
        Optional<String> empty = Optional.empty();
        Optional<String> presentValue = Optional.of("value");


        var emptySource = await(() -> empty);
        var presentSource = await(() -> presentValue);

        assertThat(present.description()).isNotBlank();
        rejection(present, (Optional<String>) null);
        rejection(absent, (Optional<String>) null);
        rejection(present, empty);
        assertSame(empty, emptySource.until(absent));
        String selected = presentSource.until(present);
        assertSame("value", selected);
        rejection(absent, presentValue);
    }

    @Test
    void optionalValueConditionsRequirePresenceAndReturnTheActualValue() {
        var expected = new String("1");
        var equalActual = new String("1");
        var differentActual = "2";
        var equal = hasValue(equalTo(expected));
        var notEqual = hasValue(notEqualTo(expected));

        var equalSource = await(() -> Optional.of(equalActual));
        var differentSource = await(() -> Optional.of(differentActual));

        rejection(equal, (Optional<String>) null);
        Optional<String> empty = Optional.empty();
        rejection(equal, empty);
        String selectedEqual = equalSource.until(equal);
        assertSame(equalActual, selectedEqual);
        rejection(notEqual, Optional.of(equalActual));
        rejection(equal, Optional.of(differentActual));
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
        assertThatNullPointerException().isThrownBy(() -> hasValue((Condition<Object, Object>) null))
                .withMessageContaining("condition");
        assertThatNullPointerException().isThrownBy(() -> hasValue((AwaitCondition.PreservingCondition<Object>) null))
                .withMessageContaining("condition");
    }
}
