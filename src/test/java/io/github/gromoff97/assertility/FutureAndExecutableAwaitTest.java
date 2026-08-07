package io.github.gromoff97.assertility;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.gromoff97.assertility.Assertility.await;
import static io.github.gromoff97.assertility.Assertility.tryAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FutureAndExecutableAwaitTest {
    @AfterEach
    void restoreAwaitilityDefaultsAndInterruptFlag() {
        Awaitility.reset();
        Thread.interrupted();
    }

    @Test
    void pendingFutureIsReturnedAfterNormalCompletion() {
        var calls = new AtomicInteger();
        var future = new CompletableFuture<String>();

        var actual = await(TestFactories.fast()).until(() -> {
            if (calls.incrementAndGet() == 2) {
                future.complete("ready");
            }
            return future;
        }).isDone();

        assertThat(actual).isSameAs(future);
        assertThat(calls).hasValue(2);
    }

    @Test
    void exceptionalAndCancelledCompletionAreDone() {
        var exceptional = new CompletableFuture<String>();
        exceptional.completeExceptionally(new IllegalStateException("failed"));
        var cancelled = new CompletableFuture<String>();
        cancelled.cancel(false);

        var exceptionalResult = await(TestFactories.fast()).until(() -> exceptional).isDone();
        var cancelledResult = tryAwait(TestFactories.fast()).until(() -> cancelled).isDone();

        assertThat(exceptionalResult).isSameAs(exceptional);
        assertThat(cancelledResult.get()).isSameAs(cancelled);
    }

    @Test
    void futureTerminalNeverInvokesGet() {
        var future = new GetRejectingFuture();

        var actual = await(TestFactories.fast()).until(() -> future).isDone();

        assertThat(actual).isSameAs(future);
        assertThat(future.getCalls).hasValue(0);
    }

    @Test
    void executableRetriesCheckedRuntimeAndAssertionFailures() {
        var calls = new AtomicInteger();

        await(TestFactories.fast()).until(() -> {
            switch (calls.incrementAndGet()) {
                case 1 -> throw new Exception("checked");
                case 2 -> throw new IllegalStateException("runtime");
                case 3 -> throw new AssertionError("assertion");
                default -> {
                }
            }
        }).as("refresh eventually succeeds").doesNotThrowAnyException();

        assertThat(calls).hasValue(4);
    }

    @Test
    void successfulTryExecutableContainsNull() {
        var result = tryAwait(TestFactories.fast()).until(() -> {
        }).doesNotThrowAnyException();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isNull();
    }

    @Test
    void executableInterruptionRestoresFlagAndPropagatesImmediately() {
        var interruption = new InterruptedException("stop");
        AwaitSources.Executable executable = () -> {
            throw interruption;
        };

        assertThatThrownBy(() -> await(TestFactories.fast()).until(executable)
                .doesNotThrowAnyException())
                .isInstanceOf(AwaitExecutionException.class)
                .hasCause(interruption);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void executablePropagatesEveryNonAssertionErrorUnchanged() {
        assertPropagatesSame(new TestVirtualMachineError());
        assertPropagatesSame(new ThreadDeath());
        assertPropagatesSame(new LinkageError("linkage"));
        assertPropagatesSame(new Error("ordinary"));
    }

    private static void assertPropagatesSame(Error failure) {
        AwaitSources.Executable executable = () -> {
            throw failure;
        };

        assertThatThrownBy(() -> await(TestFactories.fast()).until(executable)
                .doesNotThrowAnyException()).isSameAs(failure);
    }

    private static final class GetRejectingFuture implements Future<String> {
        private final AtomicInteger getCalls = new AtomicInteger();

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            getCalls.incrementAndGet();
            throw new AssertionError("get must not be called");
        }

        @Override
        public String get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            getCalls.incrementAndGet();
            throw new AssertionError("timed get must not be called");
        }
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {
    }
}
