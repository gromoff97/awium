package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.condition.Condition.PreservingStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.NarrowingStage;
import io.github.gromoff97.awium.condition.ConditionStage.ResultStage;
import io.github.gromoff97.awium.condition.Condition.SelectedStage;
import io.github.gromoff97.awium.condition.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.CollectionViewSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.MapViewSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Fluent await facade.
 *
 * @param <Observed> complete value returned by the source
 * @param <Element> value selected from an optional, collection, or map
 * @param <Family> phantom source-family marker restricting compatible selection conditions
 */
public final class Await<Observed, Element, Family extends Source<?>> extends AbstractAwait<Observed, Await<Observed, Element, Family>> {

    private Await(Source<? extends Observed> source) {
        super(source);
    }

    public static <Value> Await<Value, Value, Source<?>> await(Source<Value> source) {
        return new Await<>(source);
    }

    public static <Value> Await<Optional<Value>, Value, OptionalSource<?>> await(OptionalSource<Value> source) {
        return new Await<>(source);
    }

    public static <Element, Values extends Collection<Element>> Await<Values, Element, CollectionSource<?>> await(CollectionSource<Values> source) {
        return new Await<>(source);
    }

    public static <Element, Values extends Collection<? extends Element>>
            Await<Values, Element, CollectionSource<?>> await(CollectionViewSource<Element, Values> source) {
        return new Await<>(source);
    }

    public static <Key, Value, Entries extends Map<Key, Value>> Await<Entries, Map.Entry<Key, Value>, MapSource<?>> await(MapSource<Entries> source) {
        return new Await<>(source);
    }

    public static <Key, Value, Entries extends Map<? extends Key, ? extends Value>>
            Await<Entries, Map.Entry<? extends Key, ? extends Value>, MapSource<?>> await(MapViewSource<Key, Value, Entries> source) {
        return new Await<>(source);
    }

    public static <Value> TryAwait<Value, Value, Source<?>> tryAwait(Source<Value> source) {
        return new TryAwait<>(source);
    }

    public static <Value> TryAwait<Optional<Value>, Value, OptionalSource<?>> tryAwait(OptionalSource<Value> source) {
        return new TryAwait<>(source);
    }

    public static <Element, Values extends Collection<Element>> TryAwait<Values, Element, CollectionSource<?>> tryAwait(CollectionSource<Values> source) {
        return new TryAwait<>(source);
    }

    public static <Element, Values extends Collection<? extends Element>>
            TryAwait<Values, Element, CollectionSource<?>> tryAwait(CollectionViewSource<Element, Values> source) {
        return new TryAwait<>(source);
    }

    public static <Key, Value, Entries extends Map<Key, Value>> TryAwait<Entries, Map.Entry<Key, Value>, MapSource<?>> tryAwait(MapSource<Entries> source) {
        return new TryAwait<>(source);
    }

    public static <Key, Value, Entries extends Map<? extends Key, ? extends Value>>
            TryAwait<Entries, Map.Entry<? extends Key, ? extends Value>, MapSource<?>> tryAwait(MapViewSource<Key, Value, Entries> source) {
        return new TryAwait<>(source);
    }

    Await(Source<? extends Observed> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<Observed, Element, Family> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<Observed, Element, Family> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }

    public Observed until(PreservingStage<? super Observed> condition) {
        return complete(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Expected extends Observed> Observed until(ExpectedStage<Expected> condition) {
        return complete(ConditionRuntime.expectedEvaluator(condition), condition);
    }

    public <Expected extends Observed> List<Observed> until(ExpectedSequenceStage<Expected> condition) {
        return complete(ConditionRuntime.expectedSequenceEvaluator(condition), condition);
    }

    public <Result extends Observed> Result until(NarrowingStage<Result> condition) {
        return complete(ConditionRuntime.narrowingEvaluator(condition), condition);
    }

    public <Result> Result until(ResultStage<? super Observed, ? extends Result> condition) {
        return complete(ConditionRuntime.evaluator(condition), condition);
    }

    public Element until(SelectedStage<? super Observed, Family> condition) {
        return complete(ConditionRuntime.selectedEvaluator(condition), condition);
    }

    public List<Element> until(SelectedSequenceStage<? super Observed, Family> condition) {
        return complete(ConditionRuntime.selectedEvaluator(condition), condition);
    }
}
