package io.github.gromoff97.assertility;

import java.util.concurrent.locks.LockSupport;

final class JdkTime {

    static final NanoClock CLOCK = System::nanoTime;
    static final Parker PARKER = LockSupport::parkNanos;

    private JdkTime() {
    }
}
