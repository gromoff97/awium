package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MapAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void stateAndSizeTerminalsReturnTheExactMapInstance() {
        var key = new Key("k-1");
        var map = new LinkedHashMap<Key, Value>();
        map.put(key, new Value("ready", 10));
        var empty = new LinkedHashMap<Key, Value>();

        assertThat(await(TestFactories.fast()).until(() -> empty).isEmpty()).isSameAs(empty);
        assertThat(await(TestFactories.fast()).until(() -> map).isNotEmpty()).isSameAs(map);
        assertThat(await(TestFactories.fast()).until(() -> map).hasSize(1)).isSameAs(map);
        assertThat(await(TestFactories.fast()).until(() -> map).hasSizeGreaterThan(0))
                .isSameAs(map);
        assertThat(await(TestFactories.fast()).until(() -> map)
                .hasSizeGreaterThanOrEqualTo(1)).isSameAs(map);
        assertThat(await(TestFactories.fast()).until(() -> map).hasSizeLessThan(2))
                .isSameAs(map);
        assertThat(await(TestFactories.fast()).until(() -> map)
                .hasSizeLessThanOrEqualTo(1)).isSameAs(map);
    }

    @Test
    void keyChecksUseTheMapsKeyEqualityContract() {
        var actualKey = new Key("k-1");
        var fieldEqualButDistinct = new Key("k-1");
        var map = new LinkedHashMap<Key, Value>();
        map.put(actualKey, new Value("ready", 10));

        var present = await(TestFactories.fast()).until(() -> map).containsKey(actualKey);
        var absent = await(TestFactories.fast()).until(() -> map)
                .doesNotContainKey(fieldEqualButDistinct);
        var recursiveKeyWouldBeWrong = tryAwait(TestFactories.fast()).until(() -> map)
                .containsKey(fieldEqualButDistinct);

        assertThat(present).isSameAs(map);
        assertThat(absent).isSameAs(map);
        assertThat(recursiveKeyWouldBeWrong.isSuccess()).isFalse();
    }

    @Test
    void entryValuesUseStrictRecursiveComparison() {
        var key = new Key("k-1");
        var actualValue = new Value("ready", 10);
        var map = new LinkedHashMap<Key, Value>();
        map.put(key, actualValue);

        var recursivelyEqual = await(TestFactories.fast()).until(() -> map)
                .containsEntry(key, new Value("ready", 10));
        var strictTypeMismatch = tryAwait(TestFactories.fast()).until(() -> map)
                .containsEntry(key, new Value("ready", 10L));
        var different = await(TestFactories.fast()).until(() -> map)
                .doesNotContainEntry(key, new Value("blocked", 10));

        assertThat(recursivelyEqual).isSameAs(map);
        assertThat(strictTypeMismatch.isSuccess()).isFalse();
        assertThat(different).isSameAs(map);
    }

    @Test
    void nullKeyAndValueDistinguishPresentNullFromAbsentKey() {
        var map = new LinkedHashMap<Key, Value>();
        map.put(null, null);
        var absent = new Key("missing");

        var presentNull = await(TestFactories.fast()).until(() -> map)
                .containsEntry(null, null);
        var absentNull = await(TestFactories.fast()).until(() -> map)
                .doesNotContainEntry(absent, null);
        var missingNull = tryAwait(TestFactories.fast()).until(() -> map)
                .containsEntry(absent, null);

        assertThat(presentNull).isSameAs(map);
        assertThat(absentNull).isSameAs(map);
        assertThat(missingNull.isSuccess()).isFalse();
    }

    @Test
    void subsetAndExactContentPreserveDuplicateLookingKeys() {
        var firstKey = new Key("duplicate");
        var secondKey = new Key("duplicate");
        var firstValue = new Value("first", 1);
        var secondValue = new Value("second", 2);
        var actual = new LinkedHashMap<Key, Value>();
        actual.put(firstKey, firstValue);
        actual.put(secondKey, secondValue);
        var subset = Map.of(firstKey, new Value("first", 1));
        var expectedExact = new LinkedHashMap<Key, Value>();
        expectedExact.put(firstKey, new Value("first", 1));
        expectedExact.put(secondKey, new Value("second", 2));
        var wrongKeys = new LinkedHashMap<Key, Value>();
        wrongKeys.put(new Key("duplicate"), new Value("first", 1));
        wrongKeys.put(new Key("duplicate"), new Value("second", 2));

        var subsetResult = await(TestFactories.fast()).until(() -> actual)
                .containsAllEntriesOf(subset);
        var exactResult = await(TestFactories.fast()).until(() -> actual)
                .containsExactlyInAnyOrderEntriesOf(expectedExact);
        var wrongKeyResult = tryAwait(TestFactories.fast()).until(() -> actual)
                .containsExactlyInAnyOrderEntriesOf(wrongKeys);

        assertThat(subsetResult).isSameAs(actual);
        assertThat(exactResult).isSameAs(actual);
        assertThat(wrongKeyResult.isSuccess()).isFalse();
    }

    @Test
    void expectedMapsAndSizesAreValidatedBeforePolling() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return new LinkedHashMap<Key, Value>();
        });

        assertThatNullPointerException().isThrownBy(() -> facade.containsAllEntriesOf(null));
        assertThatNullPointerException()
                .isThrownBy(() -> facade.containsExactlyInAnyOrderEntriesOf(null));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> facade.hasSize(-1));

        assertThat(calls).hasValue(0);
    }

    private static final class Key {
        private final String id;

        private Key(String id) {
            this.id = id;
        }
    }

    private static final class Value {
        private final String status;
        private final Object amount;

        private Value(String status, Object amount) {
            this.status = status;
            this.amount = amount;
        }
    }
}
