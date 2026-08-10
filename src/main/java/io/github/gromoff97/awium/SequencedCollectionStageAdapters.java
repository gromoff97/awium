package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.SequencedCollection;

final class SequencedCollectionStageAdapters {

abstract static class SequencedCollectionConfigStages<
        E, C extends SequencedCollection<E>>
        extends CollectionStageAdapters.CollectionTerminals<E, C> {

    SequencedCollectionConfigStages(AwaitChain<C> chain) {
        super(chain);
    }

    public final SequencedCollectionUntil<E, C> stableFor(Duration stability) {
        return new SequencedCollectionTerminalStage<>(
                chain.withStableFor(stability));
    }
}

static final class SequencedCollectionInitialStage<
        E, C extends SequencedCollection<E>>
        extends SequencedCollectionConfigStages<E, C>
        implements SequencedCollectionAwait<E, C> {

    SequencedCollectionInitialStage(AwaitChain<C> chain) {
        super(chain);
    }

    @Override
    public SequencedCollectionAwait.AfterEvery<E, C> every(Duration interval) {
        return new SequencedCollectionAfterEveryStage<>(
                chain.withEvery(interval));
    }

    @Override
    public SequencedCollectionAwait.AfterUpTo<E, C> upTo(Duration timeout) {
        return new SequencedCollectionAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class SequencedCollectionAfterEveryStage<
        E, C extends SequencedCollection<E>>
        extends SequencedCollectionConfigStages<E, C>
        implements SequencedCollectionAwait.AfterEvery<E, C> {

    SequencedCollectionAfterEveryStage(AwaitChain<C> chain) {
        super(chain);
    }

    @Override
    public SequencedCollectionAwait.AfterUpTo<E, C> upTo(Duration timeout) {
        return new SequencedCollectionAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class SequencedCollectionAfterUpToStage<
        E, C extends SequencedCollection<E>>
        extends SequencedCollectionConfigStages<E, C>
        implements SequencedCollectionAwait.AfterUpTo<E, C> {

    SequencedCollectionAfterUpToStage(AwaitChain<C> chain) {
        super(chain);
    }
}

static final class SequencedCollectionTerminalStage<
        E, C extends SequencedCollection<E>>
        extends CollectionStageAdapters.CollectionTerminals<E, C>
        implements SequencedCollectionUntil<E, C> {

    SequencedCollectionTerminalStage(AwaitChain<C> chain) {
        super(chain);
    }
}
}
