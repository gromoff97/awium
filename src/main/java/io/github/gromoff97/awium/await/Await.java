package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class Await<S, E, F extends Source<?>>
        extends AbstractAwait<S, Await<S, E, F>> {

    private Await(Source<? extends S> source) {
        super(source);
    }

    public static <T> Await<T, T, Source<?>> await(Source<T> source) {
        return new Await<>(source);
    }

    public static <T> Await<Optional<T>, T, OptionalSource<?>> await(OptionalSource<T> source) {
        return new Await<>(source);
    }

    public static <E, C extends Collection<E>> Await<C, E, CollectionSource<?>> await(CollectionSource<C> source) {
        return new Await<>(source);
    }

    public static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>, MapSource<?>> await(MapSource<M> source) {
        return new Await<>(source);
    }

    public static <T> TryAwait<T, T, Source<?>> tryAwait(Source<T> source) {
        return new TryAwait<>(source);
    }

    public static <T> TryAwait<Optional<T>, T, OptionalSource<?>> tryAwait(OptionalSource<T> source) {
        return new TryAwait<>(source);
    }

    public static <E, C extends Collection<E>> TryAwait<C, E, CollectionSource<?>> tryAwait(CollectionSource<C> source) {
        return new TryAwait<>(source);
    }

    public static <K, V, M extends Map<K, V>> TryAwait<M, Map.Entry<K, V>, MapSource<?>> tryAwait(MapSource<M> source) {
        return new TryAwait<>(source);
    }

    Await(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<S, E, F> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<S, E, F> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }

    public S until(PreservingStage<? super S> condition) {
        return complete(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <R> R until(ResultStage<? super S, ? extends R> condition) {
        return complete(condition.newEvaluator(), condition);
    }

    public E until(SelectedStage<? super S, F> condition) {
        return complete(ConditionRuntime.selectedEvaluator(condition), condition);
    }

    public List<E> until(SelectedSequenceStage<? super S, F> condition) {
        return complete(ConditionRuntime.selectedEvaluator(condition), condition);
    }
}
