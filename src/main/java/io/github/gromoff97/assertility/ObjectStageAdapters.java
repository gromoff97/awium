package io.github.gromoff97.assertility;

import java.time.Duration;
import java.util.Objects;

final class ObjectStageAdapters {

abstract static class ObjectTerminals<T> {

    final AwaitChain<T> chain;

    ObjectTerminals(AwaitChain<T> chain) {
        this.chain = chain;
    }

    public final T until(PreservingCondition<? super T> condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.preserving(condition));
    }

    public final T until(ExplainedPreservingCondition<? super T> condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.preserving(condition));
    }

    public final <R> R until(Condition<? super T, ? extends R> condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.<T, R>open(condition));
    }

    public final <R> R until(
            ExplainedCondition<? super T, ? extends R> condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.<T, R>open(condition));
    }
}

abstract static class ObjectConfigStages<T> extends ObjectTerminals<T> {

    ObjectConfigStages(AwaitChain<T> chain) {
        super(chain);
    }

    public final ObjectUntil<T> stableFor(Duration stability) {
        return new ObjectTerminalStage<>(chain.withStableFor(stability));
    }
}

static final class ObjectInitialStage<T> extends ObjectConfigStages<T>
        implements ObjectAwait<T> {

    ObjectInitialStage(AwaitChain<T> chain) {
        super(chain);
    }

    @Override
    public ObjectAwait.AfterEvery<T> every(Duration interval) {
        return new ObjectAfterEveryStage<>(chain.withEvery(interval));
    }

    @Override
    public ObjectAwait.AfterUpTo<T> upTo(Duration timeout) {
        return new ObjectAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class ObjectAfterEveryStage<T> extends ObjectConfigStages<T>
        implements ObjectAwait.AfterEvery<T> {

    ObjectAfterEveryStage(AwaitChain<T> chain) {
        super(chain);
    }

    @Override
    public ObjectAwait.AfterUpTo<T> upTo(Duration timeout) {
        return new ObjectAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class ObjectAfterUpToStage<T> extends ObjectConfigStages<T>
        implements ObjectAwait.AfterUpTo<T> {

    ObjectAfterUpToStage(AwaitChain<T> chain) {
        super(chain);
    }
}

static final class ObjectTerminalStage<T> extends ObjectTerminals<T>
        implements ObjectUntil<T> {

    ObjectTerminalStage(AwaitChain<T> chain) {
        super(chain);
    }
}
}
