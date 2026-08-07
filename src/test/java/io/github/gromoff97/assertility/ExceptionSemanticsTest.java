package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.awaitUntil;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ExceptionSemanticsTest {
    @AfterEach
    void restoreAwaitilityDefaultsAndInterruptFlag() {
        Awaitility.reset();
        Thread.interrupted();
    }

    @Test
    void unignoredCheckedSourceFailureIsImmediateAndWrapped() {
        var calls = new AtomicInteger();
        var checked = new Exception("checked source failure");
        AwaitSources.Source<String> source = () -> {
            calls.incrementAndGet();
            throw checked;
        };

        assertThatThrownBy(() -> await(TestFactories.fast()).until(source).isNotNull())
                .isInstanceOf(AwaitExecutionException.class)
                .hasCause(checked);
        assertThat(calls).hasValue(1);
    }

    @Test
    void explicitlyIgnoredCheckedAndRuntimeFailuresRetry() {
        var checkedCalls = new AtomicInteger();
        var runtimeCalls = new AtomicInteger();
        var checkedFactory = TestFactories.fast().ignoreException(TransientFailure.class);
        var runtimeFactory = TestFactories.fast().ignoreException(IllegalStateException.class);
        AwaitSources.StringSource checkedSource = () -> {
            if (checkedCalls.incrementAndGet() == 1) {
                throw new TransientFailure();
            }
            return "ready";
        };
        AwaitSources.StringSource runtimeSource = () -> {
            if (runtimeCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("transient");
            }
            return "ready";
        };

        var checked = await(checkedFactory).until(checkedSource).isEqualTo("ready");
        var runtime = await(runtimeFactory).until(runtimeSource).isEqualTo("ready");

        assertThat(checked).isEqualTo("ready");
        assertThat(runtime).isEqualTo("ready");
        assertThat(checkedCalls).hasValue(2);
        assertThat(runtimeCalls).hasValue(2);
    }

    @Test
    void callbackAssertionErrorsPropagateButSatisfiesRetries() {
        var predicateFailure = new AssertionError("predicate defect");
        var extractorFailure = new AssertionError("extractor defect");
        var satisfiesCalls = new AtomicInteger();
        var ignoreExceptions = TestFactories.fast().ignoreExceptions();

        assertThatThrownBy(() -> await(ignoreExceptions).until(() -> "ready")
                .matches(value -> {
                    throw predicateFailure;
                })).isSameAs(predicateFailure);
        assertThatThrownBy(() -> await(ignoreExceptions).until(() -> "ready")
                .returns("READY", value -> {
                    throw extractorFailure;
                })).isSameAs(extractorFailure);
        var value = await(TestFactories.fast()).until(() -> "ready")
                .satisfies(ignored -> {
                    if (satisfiesCalls.incrementAndGet() < 3) {
                        throw new AssertionError("not yet");
                    }
                });

        assertThat(value).isEqualTo("ready");
        assertThat(satisfiesCalls).hasValue(3);
    }

    @Test
    void sourceAndFatalErrorsPropagateUnchangedAcrossBothModes() {
        var sourceAssertion = new AssertionError("source defect");
        var fatal = new LinkageError("fatal defect");
        AwaitSources.Source<String> assertionSource = () -> {
            throw sourceAssertion;
        };
        AwaitSources.Source<String> fatalSource = () -> {
            throw fatal;
        };

        assertThatThrownBy(() -> await(TestFactories.fast())
                .until(assertionSource)
                .isNotNull()).isSameAs(sourceAssertion);
        assertThatThrownBy(() -> tryAwait(TestFactories.fast().ignoreExceptions())
                .until(fatalSource)
                .isNotNull()).isSameAs(fatal);
    }

    @Test
    void allFinalAwaitilityFailuresBecomeAwaitFailures() {
        var timeout = tryAwait(TestFactories.fast()).until(() -> "NEW")
                .isEqualTo("COMPLETED");
        var duringCalls = new AtomicInteger();
        var during = tryAwait(TestFactories.fast().during(Duration.ofMillis(20)))
                .until(() -> duringCalls.incrementAndGet() % 2 == 0)
                .isTrue();
        var atLeast = tryAwait(TestFactories.fast().atLeast(Duration.ofMillis(50)))
                .until(() -> "ready")
                .isEqualTo("ready");
        AwaitSources.StringSource ignoredSource = () -> {
            throw new TransientFailure();
        };
        var ignoredExhaustion = tryAwait(TestFactories.fast()
                .ignoreException(TransientFailure.class)).until(ignoredSource).isNotNull();
        var failFast = tryAwait(TestFactories.fast().failFast(() -> true))
                .until(() -> "NEW")
                .isEqualTo("COMPLETED");

        assertThat(List.of(timeout, during, atLeast, ignoredExhaustion, failFast))
                .allSatisfy(result -> {
                    assertThat(result.isSuccess()).isFalse();
                    assertThat(result.failure().orElseThrow().getCause())
                            .isInstanceOfAny(
                                    ConditionTimeoutException.class,
                                    TerminalFailureException.class);
                });
    }

    @Test
    void diagnosticsPreserveAsAliasAndRecursiveFieldPath() {
        var expected = new Envelope(new Payment("p-1", "COMPLETED"));
        var actual = new Envelope(new Payment("p-1", "NEW"));

        var failure = catchThrowableOfType(
                () -> await(TestFactories.fast().alias("payment-alias"))
                        .until(() -> actual)
                        .as("Payment p-1")
                        .isEqualTo(expected),
                AwaitFailure.class);

        assertThat(failure.getMessage())
                .startsWith("Payment p-1: isEqualTo did not complete: ")
                .containsOnlyOnce("Payment p-1");
        assertThat(failure.getCause())
                .isInstanceOf(ConditionTimeoutException.class)
                .hasMessageContaining("payment-alias");
        assertThat(allCauseMessages(failure)).contains("status");
    }

    @Test
    void duringReturnsTheFinalSuccessfulObservationWithoutIdentityTracking() {
        var calls = new AtomicInteger();

        var selected = await(TestFactories.fast().during(Duration.ofMillis(20)))
                .until(() -> List.of(new Observation(calls.incrementAndGet())))
                .any(observation -> true);

        assertThat(calls.get()).isGreaterThan(1);
        assertThat(selected.number()).isEqualTo(calls.get());
    }

    @Test
    void interruptionCannotBeConvertedIntoAnIgnoredTimeout() {
        var interruption = new InterruptedException("stop");
        AwaitSources.Source<String> source = () -> {
            throw interruption;
        };

        assertThatThrownBy(() -> await(TestFactories.fast().ignoreExceptions())
                .until(source)
                .isNotNull())
                .isInstanceOf(AwaitExecutionException.class)
                .hasCause(interruption);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void completeValidationMatrixRunsBeforeAnySource() {
        var collectionCalls = new AtomicInteger();
        var stringCalls = new AtomicInteger();
        var mapCalls = new AtomicInteger();
        var collection = await(TestFactories.fast()).until(() -> {
            collectionCalls.incrementAndGet();
            return new ArrayList<>(List.of("ready"));
        });
        var string = await(TestFactories.fast()).until(() -> {
            stringCalls.incrementAndGet();
            return "ready";
        });
        var map = await(TestFactories.fast()).until(() -> {
            mapCalls.incrementAndGet();
            return new LinkedHashMap<String, String>();
        });

        assertThatNullPointerException()
                .isThrownBy(() -> await((org.awaitility.core.ConditionFactory) null));
        assertThatNullPointerException()
                .isThrownBy(() -> awaitUntil((AwaitSources.Source<String>) null));
        assertThatNullPointerException()
                .isThrownBy(() -> collection.single((Predicate<String>) null));
        assertThatNullPointerException()
                .isThrownBy(() -> collection.single((Function<String, String>) null, "ready"));
        assertThatNullPointerException()
                .isThrownBy(() -> collection.satisfies((Consumer<List<String>>) null));
        assertThatNullPointerException().isThrownBy(() -> collection.containsAll(null));
        assertThatNullPointerException().isThrownBy(() -> map.containsAllEntriesOf(null));
        assertThatNullPointerException()
                .isThrownBy(() -> collection.single((String) null, value -> true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> collection.single(" ", value -> true));
        assertThatIllegalArgumentException().isThrownBy(() -> collection.hasSize(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> collection.exactly(1, value -> true));
        assertThatNullPointerException()
                .isThrownBy(() -> collection.contains((String[]) null));
        assertThatNullPointerException()
                .isThrownBy(() -> string.contains("ready", null));
        assertThatThrownBy(() -> string.as("value %d", "wrong"))
                .isInstanceOf(java.util.IllegalFormatConversionException.class);

        assertThat(collectionCalls).hasValue(0);
        assertThat(stringCalls).hasValue(0);
        assertThat(mapCalls).hasValue(0);
    }

    @Test
    void nullExpectedValuesRemainSupported() {
        AwaitSources.StringSource nullString = () -> null;
        var collection = new ArrayList<String>();
        collection.add(null);
        var map = new LinkedHashMap<String, String>();
        map.put(null, null);

        assertThat(await(TestFactories.fast()).until(nullString).isEqualTo(null)).isNull();
        assertThat(await(TestFactories.fast()).until(() -> collection)
                .contains(new String[]{null})).isSameAs(collection);
        assertThat(await(TestFactories.fast()).until(() -> map)
                .containsEntry(null, null)).isSameAs(map);
    }

    private static String allCauseMessages(Throwable failure) {
        var messages = new StringBuilder();
        for (var current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append(System.lineSeparator());
            }
        }
        return messages.toString();
    }

    private static final class TransientFailure extends Exception {
    }

    private record Payment(String id, String status) {
    }

    private record Envelope(Payment payment) {
    }

    private record Observation(int number) {
    }
}
