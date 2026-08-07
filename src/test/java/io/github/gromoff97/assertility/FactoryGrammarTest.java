package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.IllegalFormatConversionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.awaitUntil;
import static io.github.gromoff97.assertility.Assertility.tryAwaitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactoryGrammarTest {
    @AfterEach
    void restoreAwaitilityDefaults() {
        Awaitility.reset();
    }

    @Test
    void savedFactoryStageCreatesIndependentSourceFacades() {
        var stage = await(TestFactories.fast());

        var first = stage.until(() -> "first").isEqualTo("first");
        var second = stage.until(() -> "second").isEqualTo("second");

        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
    }

    @Test
    void nullFactoryAndSourcesFailBeforePolling() {
        var calls = new AtomicInteger();

        assertThatNullPointerException()
                .isThrownBy(() -> await((ConditionFactory) null))
                .withMessage("factory");
        assertThatNullPointerException()
                .isThrownBy(() -> await(TestFactories.fast())
                        .until((AwaitSources.Source<String>) null))
                .withMessage("source");
        assertThatNullPointerException()
                .isThrownBy(() -> awaitUntil((AwaitSources.Source<String>) null))
                .withMessage("source");

        assertThat(calls).hasValue(0);
    }

    @Test
    void callbacksAndDescriptionsAreValidatedBeforePolling() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return "ready";
        });

        assertThatNullPointerException().isThrownBy(() -> facade.matches((Predicate<String>) null));
        assertThatNullPointerException().isThrownBy(() -> facade.matches("ready", null));
        assertThatNullPointerException().isThrownBy(() -> facade.returns("ready", (Function<String, String>) null));
        assertThatNullPointerException().isThrownBy(() -> facade.satisfies((Consumer<String>) null));
        assertThatThrownBy(() -> facade.matches("  ", value -> true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(calls).hasValue(0);
    }

    @Test
    void invalidAsFormattingFailsImmediately() {
        var calls = new AtomicInteger();
        var facade = await(TestFactories.fast()).until(() -> {
            calls.incrementAndGet();
            return "ready";
        });

        assertThatThrownBy(() -> facade.as("payment %d", "not-a-number"))
                .isInstanceOf(IllegalFormatConversionException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void oneArgumentAsTreatsPercentTokensLiterally() {
        assertThatThrownBy(() -> await(TestFactories.fast()).until(() -> "NEW")
                .as("payment %s must complete")
                .isEqualTo("COMPLETED"))
                .isInstanceOf(AwaitFailure.class)
                .hasMessageContaining("payment %s must complete");
    }

    @Test
    void defaultEntryObservesCurrentAwaitilityDefaults() {
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        Awaitility.setDefaultPollInterval(Duration.ofMillis(2));
        Awaitility.setDefaultTimeout(Duration.ofMillis(35));

        var result = tryAwaitUntil(() -> "NEW").isEqualTo("COMPLETED");
        var failure = result.failure().orElseThrow();

        assertThat(failure.getCause()).isInstanceOf(ConditionTimeoutException.class);
        assertThat(failure).hasMessageContaining("35 milliseconds");
    }
}
