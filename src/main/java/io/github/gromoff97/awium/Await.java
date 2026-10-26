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
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
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

    public static <VALUE> ValueAwait<VALUE> await(Callable<VALUE> source) {
        return new ValueAwait<>(source, DEFAULT);
    }

    public static <VALUE> Await<Optional<VALUE>, VALUE, Void> await(OptionalSource<VALUE> source) {
        return new Await<>(source, DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<ELEMENT>> Await<VALUES, ELEMENT, Void> await(CollectionSource<VALUES> source) {
        return new Await<>(source, DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            Await<VALUES, ELEMENT, Void> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new Await<>(source, DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> Await<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new Await<>(source, DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            Await<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new Await<>(source, DEFAULT);
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
        return complete(ConditionAwaitAttempt.bind(required(condition).definition));
    }

    public MAP_VALUE until(MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>> condition) {
        return complete(ConditionAwaitAttempt.bind(required(condition).definition));
    }

    public AwaitResult<OBSERVED, ELEMENT> tryUntil(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>> condition) {
        return capture(ConditionAwaitAttempt.bind(required(condition).definition));
    }

    public AwaitResult<OBSERVED, MAP_VALUE> tryUntil(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>> condition) {
        return capture(ConditionAwaitAttempt.bind(required(condition).definition));
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

    /** A source that allows {@code present} and {@code hasValue} to return its contained type. */
    @FunctionalInterface
    public interface OptionalSource<VALUE> extends Callable<Optional<VALUE>> {
    }

    /** A collection source that retains both its complete collection type and selected element type. */
    @FunctionalInterface
    public interface CollectionSource<VALUES extends Collection<?>> extends Callable<VALUES> {
    }

    /**
     * Adapts a covariant collection source while retaining its element bound for selections.
     * The returned collection is neither copied nor changed.
     * Use a typed declaration or explicit type arguments to establish the selected element bound.
     *
     * @param <ELEMENT> upper bound of selected elements
     * @param <VALUES> complete collection type returned by the source
     * @param delegate source of the original collection
     */
    public record CollectionViewSource<ELEMENT, VALUES extends Collection<? extends ELEMENT>>(
            Callable<? extends VALUES> delegate) implements Callable<VALUES> {

        public CollectionViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public VALUES call() throws Exception {
            return delegate.call();
        }
    }

    /** A map source that retains its complete map type, entry type, and value type. */
    @FunctionalInterface
    public interface MapSource<ENTRIES extends Map<?, ?>> extends Callable<ENTRIES> {
    }

    /**
     * Adapts a covariant map source while retaining its key and value bounds for selections.
     * The returned map is neither copied nor changed.
     * Use a typed declaration or explicit type arguments to establish the selected bounds.
     *
     * @param <KEY> upper bound of selected keys
     * @param <VALUE> upper bound of selected values
     * @param <ENTRIES> complete map type returned by the source
     * @param delegate source of the original map
     */
    public record MapViewSource<KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>(
            Callable<? extends ENTRIES> delegate) implements Callable<ENTRIES> {

        public MapViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public ENTRIES call() throws Exception {
            return delegate.call();
        }
    }
}
