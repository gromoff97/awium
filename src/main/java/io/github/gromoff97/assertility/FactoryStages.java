package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.AwaitFactory;
import io.github.gromoff97.assertility.api.BooleanAwait;
import io.github.gromoff97.assertility.api.ComparableAwait;
import io.github.gromoff97.assertility.api.CollectionAwait;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.OptionalAwait;
import io.github.gromoff97.assertility.api.SequencedCollectionAwait;
import io.github.gromoff97.assertility.api.StringAwait;
import io.github.gromoff97.assertility.api.TryAwaitFactory;
import io.github.gromoff97.assertility.api.TryBooleanAwait;
import io.github.gromoff97.assertility.api.TryComparableAwait;
import io.github.gromoff97.assertility.api.TryCollectionAwait;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import io.github.gromoff97.assertility.api.TryOptionalAwait;
import io.github.gromoff97.assertility.api.TrySequencedCollectionAwait;
import io.github.gromoff97.assertility.api.TryStringAwait;
import org.awaitility.core.ConditionFactory;

import java.util.Collection;
import java.util.SequencedCollection;

final class FactoryStages {
    private FactoryStages() {
    }

    static AwaitFactory throwing(ConditionFactory factory) {
        return new ThrowingFactory(factory);
    }

    static TryAwaitFactory result(ConditionFactory factory) {
        return new ResultFactory(factory);
    }

    private record ThrowingFactory(ConditionFactory factory) implements AwaitFactory {
        @Override
        public BooleanAwait until(AwaitSources.BooleanSource source) {
            return Facades.bool(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T extends Comparable<? super T>> ComparableAwait<T> until(
                AwaitSources.ComparableSource<T> source) {
            return Facades.comparable(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public StringAwait until(AwaitSources.StringSource source) {
            return Facades.string(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T> OptionalAwait<T> until(AwaitSources.OptionalSource<T> source) {
            return Facades.optional(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <E, C extends SequencedCollection<E>> SequencedCollectionAwait<E, C> until(
                AwaitSources.SequencedCollectionSource<E, C> source) {
            return Facades.sequencedCollection(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <E, C extends Collection<E>> CollectionAwait<E, C> until(
                AwaitSources.CollectionSource<E, C> source) {
            return Facades.collection(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T> ObjectAwait<T> until(AwaitSources.Source<T> source) {
            return Facades.object(new AwaitSpec<>(factory, source, null));
        }
    }

    private record ResultFactory(ConditionFactory factory) implements TryAwaitFactory {
        @Override
        public TryBooleanAwait until(AwaitSources.BooleanSource source) {
            return Facades.tryBool(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T extends Comparable<? super T>> TryComparableAwait<T> until(
                AwaitSources.ComparableSource<T> source) {
            return Facades.tryComparable(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public TryStringAwait until(AwaitSources.StringSource source) {
            return Facades.tryString(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T> TryOptionalAwait<T> until(AwaitSources.OptionalSource<T> source) {
            return Facades.tryOptional(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <E, C extends SequencedCollection<E>> TrySequencedCollectionAwait<E, C> until(
                AwaitSources.SequencedCollectionSource<E, C> source) {
            return Facades.trySequencedCollection(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <E, C extends Collection<E>> TryCollectionAwait<E, C> until(
                AwaitSources.CollectionSource<E, C> source) {
            return Facades.tryCollection(new AwaitSpec<>(factory, source, null));
        }

        @Override
        public <T> TryObjectAwait<T> until(AwaitSources.Source<T> source) {
            return Facades.tryObject(new AwaitSpec<>(factory, source, null));
        }
    }
}
