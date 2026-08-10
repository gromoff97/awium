package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

final class OptionalStageAdapters {

abstract static class OptionalTerminals<T>
        extends ObjectStageAdapters.ObjectTerminals<Optional<T>> {

    OptionalTerminals(AwaitChain<Optional<T>> chain) {
        super(chain);
    }

    public final T until(Present condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.present(condition));
    }

    public final T until(ExplainedPresent condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.present(condition));
    }
}

abstract static class OptionalConfigStages<T> extends OptionalTerminals<T> {

    OptionalConfigStages(AwaitChain<Optional<T>> chain) {
        super(chain);
    }

    public final OptionalUntil<T> stableFor(Duration stability) {
        return new OptionalTerminalStage<>(chain.withStableFor(stability));
    }
}

static final class OptionalInitialStage<T> extends OptionalConfigStages<T>
        implements OptionalAwait<T> {

    OptionalInitialStage(AwaitChain<Optional<T>> chain) {
        super(chain);
    }

    @Override
    public OptionalAwait.AfterEvery<T> every(Duration interval) {
        return new OptionalAfterEveryStage<>(chain.withEvery(interval));
    }

    @Override
    public OptionalAwait.AfterUpTo<T> upTo(Duration timeout) {
        return new OptionalAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class OptionalAfterEveryStage<T> extends OptionalConfigStages<T>
        implements OptionalAwait.AfterEvery<T> {

    OptionalAfterEveryStage(AwaitChain<Optional<T>> chain) {
        super(chain);
    }

    @Override
    public OptionalAwait.AfterUpTo<T> upTo(Duration timeout) {
        return new OptionalAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class OptionalAfterUpToStage<T> extends OptionalConfigStages<T>
        implements OptionalAwait.AfterUpTo<T> {

    OptionalAfterUpToStage(AwaitChain<Optional<T>> chain) {
        super(chain);
    }
}

static final class OptionalTerminalStage<T> extends OptionalTerminals<T>
        implements OptionalUntil<T> {

    OptionalTerminalStage(AwaitChain<Optional<T>> chain) {
        super(chain);
    }
}
}
