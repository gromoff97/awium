package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

final class CollectionStages {

    abstract static class CollectionTerminals<E, C extends Collection<E>>
            extends ObjectStages.ObjectTerminals<C> {

        CollectionTerminals(AwaitChain<C> chain) {
            super(chain);
        }

        public final C until(StructuralCondition condition) {
            Objects.requireNonNull(condition, "condition must not be null");
            chain.config().validatePair();
            return chain.execute(ConditionRuntime.structural(
                    condition, "collection", Collection::size));
        }

        public final C until(StructuralCondition.Explained condition) {
            Objects.requireNonNull(condition, "condition must not be null");
            chain.config().validatePair();
            return chain.execute(ConditionRuntime.structural(
                    condition, "collection", Collection::size));
        }
    }

    abstract static class CollectionConfigStages<E, C extends Collection<E>>
            extends CollectionTerminals<E, C> {

        CollectionConfigStages(AwaitChain<C> chain) {
            super(chain);
        }

        public final CollectionAwait.Until<E, C> stableFor(Duration stability) {
            return new CollectionTerminalStage<>(chain.withStableFor(stability));
        }
    }

    static final class CollectionInitialStage<E, C extends Collection<E>>
            extends CollectionConfigStages<E, C> implements CollectionAwait<E, C> {

        CollectionInitialStage(AwaitChain<C> chain) {
            super(chain);
        }

        @Override
        public CollectionAwait.AfterEvery<E, C> every(Duration interval) {
            return new CollectionAfterEveryStage<>(chain.withEvery(interval));
        }

        @Override
        public CollectionAwait.AfterUpTo<E, C> upTo(Duration timeout) {
            return new CollectionAfterUpToStage<>(chain.withUpTo(timeout));
        }
    }

    static final class CollectionAfterEveryStage<E, C extends Collection<E>>
            extends CollectionConfigStages<E, C>
            implements CollectionAwait.AfterEvery<E, C> {

        CollectionAfterEveryStage(AwaitChain<C> chain) {
            super(chain);
        }

        @Override
        public CollectionAwait.AfterUpTo<E, C> upTo(Duration timeout) {
            return new CollectionAfterUpToStage<>(chain.withUpTo(timeout));
        }
    }

    static final class CollectionAfterUpToStage<E, C extends Collection<E>>
            extends CollectionConfigStages<E, C>
            implements CollectionAwait.AfterUpTo<E, C> {

        CollectionAfterUpToStage(AwaitChain<C> chain) {
            super(chain);
        }
    }

    static final class CollectionTerminalStage<E, C extends Collection<E>>
            extends CollectionTerminals<E, C> implements CollectionAwait.Until<E, C> {

        CollectionTerminalStage(AwaitChain<C> chain) {
            super(chain);
        }
    }
}
