package io.github.gromoff97.assertility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwaitResultTest {
    @Test
    void successCanContainNull() {
        var result = AwaitResult.<String>success(null);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isNull();
        assertThat(result.failure()).isEmpty();
    }

    @Test
    void failedGetRethrowsTheStoredInstance() {
        var failure = new AwaitFailure("payment did not appear", new AssertionError("missing"));
        var result = AwaitResult.<String>failed(failure);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).containsSame(failure);
        assertThatThrownBy(result::get).isSameAs(failure);
    }
}
