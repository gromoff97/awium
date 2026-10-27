package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static java.util.Objects.requireNonNull;

/**
 * Fluent await facade. A single condition returns one value; an array or several compatible conditions
 * capture a list from successive observations. During persistence, only the final stage is re-evaluated.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <ELEMENT> value selected from an optional, collection, or map
 * @param <MAP_VALUE> value selected from a map by key
 */
public final class Await<OBSERVED, ELEMENT, MAP_VALUE> extends ValueAwait<OBSERVED> {

    Await(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    /** Configures a wait for an observed value. Container lambdas select a specialized overload. */
    public static <VALUE> ValueAwait<VALUE> await(Callable<? extends VALUE> source) {
        return new ValueAwait<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures an optional wait, retaining the contained value type. */
    public static <VALUE> Await<Optional<VALUE>, VALUE, Void> await(OptionalSource<VALUE> source) {
        return new Await<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a collection wait, retaining its complete type and element type. */
    public static <ELEMENT, VALUES extends Collection<ELEMENT>> Await<VALUES, ELEMENT, Void> await(CollectionSource<VALUES> source) {
        return new Await<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a map wait, retaining its complete type, entry type, and value type. */
    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> Await<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new Await<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a wait with explicit bounds for a covariant collection source. */
    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            Await<VALUES, ELEMENT, Void> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new Await<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    /** Configures a wait with explicit bounds for a covariant map source. */
    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            Await<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new Await<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
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

    @Override
    public Await<OBSERVED, ELEMENT, MAP_VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return reconfigured(configuration.withTime(clock, parker));
    }

    private Await<OBSERVED, ELEMENT, MAP_VALUE> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, configuration);
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>> condition) {
        return complete(ConditionAwaitAttempt.bind(required(condition)));
    }

    public MAP_VALUE until(MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>> condition) {
        return complete(ConditionAwaitAttempt.bind(required(condition)));
    }

    public AwaitResult<OBSERVED, ELEMENT> tryUntil(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>> condition) {
        return capture(ConditionAwaitAttempt.bind(required(condition)));
    }

    public AwaitResult<OBSERVED, MAP_VALUE> tryUntil(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>> condition) {
        return capture(ConditionAwaitAttempt.bind(required(condition)));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<ELEMENT> until(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<MAP_VALUE> until(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<ELEMENT>> tryUntil(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<MAP_VALUE>> tryUntil(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    /** A source whose optional value can be selected by a condition. */
    @FunctionalInterface
    public interface OptionalSource<VALUE> extends Callable<Optional<VALUE>> {}

    /** A source retaining both the complete collection type and its selected element type. */
    @FunctionalInterface
    public interface CollectionSource<VALUES extends Collection<?>> extends Callable<VALUES> {}

    /** A source retaining the complete map type and its selected key and value types. */
    @FunctionalInterface
    public interface MapSource<ENTRIES extends Map<?, ?>> extends Callable<ENTRIES> {}

    /**
     * Describes a covariant collection source with an explicit element bound for selections.
     * The returned collection is neither copied nor changed.
     * Use a typed declaration or explicit type arguments to establish the selected element bound.
     *
     * @param <ELEMENT> upper bound of selected elements
     * @param <VALUES> complete collection type returned by the source
     * @param delegate source of the original collection
     */
    public record CollectionViewSource<ELEMENT, VALUES extends Collection<? extends ELEMENT>>(
            Callable<? extends VALUES> delegate) {

        public CollectionViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }
    }

    /**
     * Describes a covariant map source with explicit key and value bounds for selections.
     * The returned map is neither copied nor changed.
     * Use a typed declaration or explicit type arguments to establish the selected bounds.
     *
     * @param <KEY> upper bound of selected keys
     * @param <VALUE> upper bound of selected values
     * @param <ENTRIES> complete map type returned by the source
     * @param delegate source of the original map
     */
    public record MapViewSource<KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>(
            Callable<? extends ENTRIES> delegate) {

        public MapViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }
    }
}
