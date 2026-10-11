package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

final class CheckedCalls {

    private CheckedCalls() {}

    static <Result> Result call(Callable<? extends Result> callback) throws Exception {
        checkInterruption();
        return callback.call();
    }

    static void checkInterruption() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("caller thread interrupt flag was set");
        }
    }
}
