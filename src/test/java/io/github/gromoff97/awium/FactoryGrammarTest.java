package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static java.time.Duration.*;

import io.github.gromoff97.awium.condition.*;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import io.github.gromoff97.awium.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        var pollingTime = new FakeTime(0);
        assertNull("source", () -> await((Source<Object>) null).usingTime(pollingTime, pollingTime));
        assertNull("source", () -> await(
                (CollectionSource<Collection<Object>>) null).usingTime(pollingTime, pollingTime));
        assertNull("source", () -> await(
                (MapSource<Map<Object, Object>>) null).usingTime(pollingTime, pollingTime));
    }

    @Test
    void repeatedConfigurationUsesTheLastValueWithoutMutatingEarlierStages() {
        var pollingTime = new FakeTime(0);
        int[] calls = {0};
        var initial = await(() -> "v" + ++calls[0]).usingTime(pollingTime, pollingTime);

        var slow = initial.every(ofSeconds(20));
        var repaired = slow.upTo(ofSeconds(10)).every(ofMillis(1)).upTo(ofSeconds(1))
                .persisting(ofSeconds(2)).persisting(ZERO);

        assertEquals("v2", repaired.until(equalTo("v2")));
        assertEquals(ofMillis(1).toNanos(), pollingTime.getAsLong());
        assertThrows(AwaitFailure.AwaitTimeoutException.class,
                () -> slow.until(equalTo("never")));
        assertEquals(3, calls[0]);
        assertEquals(ofSeconds(10).plusMillis(1).toNanos(), pollingTime.getAsLong());
    }

    @Test
    void everyTerminalOverloadRejectsNullConditions() {
        var pollingTime = new FakeTime(0);
        var object = await((Source<String>) () -> "value").usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNull("condition", () -> object.until((PreservingCondition<String>) null));
        assertNull("condition", () -> object.until((Condition<String, String>) null));

        var optional = await((OptionalSource<String>) Optional::empty).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNull("condition", () -> optional.until((SelectedCondition<Optional<?>, OptionalSource<?>>) null));

        var collection = await((CollectionSource<Collection<String>>) List::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNull("condition", () -> collection.until((SelectedCondition<Collection<?>, CollectionSource<?>>) null));

        var map = await((MapSource<Map<String, String>>) Map::of).usingTime(pollingTime, pollingTime).every(ofSeconds(20));
        assertNull("condition", () -> map.until((SelectedCondition<Map<?, ?>, MapSource<?>>) null));
    }

    private static void assertNull(String context, Executable action) {
        assertTrue(assertThrows(NullPointerException.class, action).getMessage().contains(context));
    }
}
