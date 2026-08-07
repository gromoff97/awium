package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.AwaitFactory;
import io.github.gromoff97.assertility.api.BooleanAwait;
import io.github.gromoff97.assertility.api.ComparableAwait;
import io.github.gromoff97.assertility.api.CollectionAwait;
import io.github.gromoff97.assertility.api.ExecutableAwait;
import io.github.gromoff97.assertility.api.FutureAwait;
import io.github.gromoff97.assertility.api.MapAwait;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.OptionalAwait;
import io.github.gromoff97.assertility.api.SequencedCollectionAwait;
import io.github.gromoff97.assertility.api.StringAwait;
import io.github.gromoff97.assertility.api.TryAwaitFactory;
import io.github.gromoff97.assertility.api.TryBooleanAwait;
import io.github.gromoff97.assertility.api.TryComparableAwait;
import io.github.gromoff97.assertility.api.TryCollectionAwait;
import io.github.gromoff97.assertility.api.TryExecutableAwait;
import io.github.gromoff97.assertility.api.TryFutureAwait;
import io.github.gromoff97.assertility.api.TryMapAwait;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import io.github.gromoff97.assertility.api.TryOptionalAwait;
import io.github.gromoff97.assertility.api.TrySequencedCollectionAwait;
import io.github.gromoff97.assertility.api.TryStringAwait;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.util.Collection;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.concurrent.Future;

public final class Assertility {
    private Assertility() {
    }

    public static BooleanAwait awaitUntil(AwaitSources.BooleanSource source) {
        return Facades.bool(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T extends Comparable<? super T>> ComparableAwait<T> awaitUntil(
            AwaitSources.ComparableSource<T> source) {
        return Facades.comparable(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static StringAwait awaitUntil(AwaitSources.StringSource source) {
        return Facades.string(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> OptionalAwait<T> awaitUntil(AwaitSources.OptionalSource<T> source) {
        return Facades.optional(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <E, C extends SequencedCollection<E>> SequencedCollectionAwait<E, C>
            awaitUntil(AwaitSources.SequencedCollectionSource<E, C> source) {
        return Facades.sequencedCollection(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <E, C extends Collection<E>> CollectionAwait<E, C> awaitUntil(
            AwaitSources.CollectionSource<E, C> source) {
        return Facades.collection(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <K, V, M extends Map<K, V>> MapAwait<K, V, M> awaitUntil(
            AwaitSources.MapSource<K, V, M> source) {
        return Facades.map(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <F extends Future<?>> FutureAwait<F> awaitUntil(
            AwaitSources.FutureSource<F> source) {
        return Facades.future(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static ExecutableAwait awaitUntil(AwaitSources.Executable source) {
        return Facades.executable(Awaitility.await(), source);
    }

    public static <T> ObjectAwait<T> awaitUntil(AwaitSources.Source<T> source) {
        return Facades.object(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static TryBooleanAwait tryAwaitUntil(AwaitSources.BooleanSource source) {
        return Facades.tryBool(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T extends Comparable<? super T>> TryComparableAwait<T> tryAwaitUntil(
            AwaitSources.ComparableSource<T> source) {
        return Facades.tryComparable(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static TryStringAwait tryAwaitUntil(AwaitSources.StringSource source) {
        return Facades.tryString(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> TryOptionalAwait<T> tryAwaitUntil(AwaitSources.OptionalSource<T> source) {
        return Facades.tryOptional(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <E, C extends SequencedCollection<E>> TrySequencedCollectionAwait<E, C>
            tryAwaitUntil(AwaitSources.SequencedCollectionSource<E, C> source) {
        return Facades.trySequencedCollection(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <E, C extends Collection<E>> TryCollectionAwait<E, C> tryAwaitUntil(
            AwaitSources.CollectionSource<E, C> source) {
        return Facades.tryCollection(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <K, V, M extends Map<K, V>> TryMapAwait<K, V, M> tryAwaitUntil(
            AwaitSources.MapSource<K, V, M> source) {
        return Facades.tryMap(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <F extends Future<?>> TryFutureAwait<F> tryAwaitUntil(
            AwaitSources.FutureSource<F> source) {
        return Facades.tryFuture(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static TryExecutableAwait tryAwaitUntil(AwaitSources.Executable source) {
        return Facades.tryExecutable(Awaitility.await(), source);
    }

    public static <T> TryObjectAwait<T> tryAwaitUntil(AwaitSources.Source<T> source) {
        return Facades.tryObject(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static AwaitFactory await(ConditionFactory factory) {
        return FactoryStages.throwing(Validation.factory(factory));
    }

    public static TryAwaitFactory tryAwait(ConditionFactory factory) {
        return FactoryStages.result(Validation.factory(factory));
    }
}
