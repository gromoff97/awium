package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;

final class TestFactories {
    private TestFactories() {
    }

    static ConditionFactory fast() {
        return Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(2))
                .atMost(Duration.ofMillis(150));
    }
}
