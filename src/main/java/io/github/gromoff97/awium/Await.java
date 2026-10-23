package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Source.CollectionSource;
import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import io.github.gromoff97.awium.Source.OptionalSource;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static java.util.Objects.requireNonNull;

/**
 * Fluent await facade. One condition returns one value; several compatible conditions capture a list
 * from successive observations. During persistence, only the final stage is re-evaluated.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <ELEMENT> value selected from an optional, collection, or map
 */
public final class Await<OBSERVED, ELEMENT, MAP_VALUE> extends ValueAwait<OBSERVED> {

    private Await(Source<? extends OBSERVED> source) {
        super(source);
    }

    private Await(Source<? extends OBSERVED> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    public static <VALUE> ValueAwait<VALUE> await(Source<VALUE> source) {
        return new ValueAwait<>(source);
    }

    public static <VALUE> Await<Optional<VALUE>, VALUE, Void> await(OptionalSource<VALUE> source) {
        return new Await<>(source);
    }

    public static <ELEMENT, VALUES extends Collection<ELEMENT>> Await<VALUES, ELEMENT, Void> await(CollectionSource<VALUES> source) {
        return new Await<>(source);
    }

    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            Await<VALUES, ELEMENT, Void> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new Await<>(source);
    }

    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> Await<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new Await<>(source);
    }

    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            Await<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new Await<>(source);
    }

    @Override
    public Await<OBSERVED, ELEMENT, MAP_VALUE> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    @Override
    public Await<OBSERVED, ELEMENT, MAP_VALUE> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    @Override
    public Await<OBSERVED, ELEMENT, MAP_VALUE> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    /**
     * Uses a monotonic nanosecond clock and a wait operation receiving a duration in nanoseconds.
     * Both operations must share the same time base; a wait may return early.
     */
    @Override
    public Await<OBSERVED, ELEMENT, MAP_VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration,
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    private Await<OBSERVED, ELEMENT, MAP_VALUE> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, configuration, clock, parker);
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return complete(required(condition).<ELEMENT>selectionAttempt());
    }

    public MAP_VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return complete(required(condition).<MAP_VALUE>selectionAttempt());
    }

    public AwaitResult<OBSERVED, ELEMENT> tryUntil(SelectedCondition<? super OBSERVED> condition) {
        return capture(required(condition).<ELEMENT>selectionAttempt());
    }

    public AwaitResult<OBSERVED, MAP_VALUE> tryUntil(MapValueCondition<? super OBSERVED> condition) {
        return capture(required(condition).<MAP_VALUE>selectionAttempt());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<ELEMENT> until(SelectedCondition<? super OBSERVED> first, SelectedCondition<? super OBSERVED> second, SelectedCondition<? super OBSERVED>... rest) {
        return until(ConditionSupport.<OBSERVED, ELEMENT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<MAP_VALUE> until(MapValueCondition<? super OBSERVED> first, MapValueCondition<? super OBSERVED> second, MapValueCondition<? super OBSERVED>... rest) {
        return until(ConditionSupport.<OBSERVED, MAP_VALUE>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<ELEMENT>> tryUntil(SelectedCondition<? super OBSERVED> first, SelectedCondition<? super OBSERVED> second, SelectedCondition<? super OBSERVED>... rest) {
        return tryUntil(ConditionSupport.<OBSERVED, ELEMENT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<MAP_VALUE>> tryUntil(MapValueCondition<? super OBSERVED> first, MapValueCondition<? super OBSERVED> second, MapValueCondition<? super OBSERVED>... rest) {
        return tryUntil(ConditionSupport.<OBSERVED, MAP_VALUE>captured(first, second, rest));
    }
}
