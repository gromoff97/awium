package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
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

    private Await(Callable<? extends OBSERVED> source) {
        super(source);
    }

    private Await(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    public static <VALUE> ValueAwait<VALUE> await(Callable<VALUE> source) {
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
        return reconfigured(configuration.withTime(clock, parker));
    }

    private Await<OBSERVED, ELEMENT, MAP_VALUE> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, configuration);
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return complete(required(condition).selectionAttempt());
    }

    public MAP_VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return complete(required(condition).selectionAttempt());
    }

    public AwaitResult<OBSERVED, ELEMENT> tryUntil(SelectedCondition<? super OBSERVED> condition) {
        return capture(required(condition).selectionAttempt());
    }

    public AwaitResult<OBSERVED, MAP_VALUE> tryUntil(MapValueCondition<? super OBSERVED> condition) {
        return capture(required(condition).selectionAttempt());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<ELEMENT> until(SelectedCondition<? super OBSERVED>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<MAP_VALUE> until(MapValueCondition<? super OBSERVED>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<ELEMENT>> tryUntil(SelectedCondition<? super OBSERVED>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<MAP_VALUE>> tryUntil(MapValueCondition<? super OBSERVED>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    @FunctionalInterface
    public interface OptionalSource<VALUE> extends Callable<Optional<VALUE>> {
    }

    @FunctionalInterface
    public interface CollectionSource<VALUES extends Collection<?>> extends Callable<VALUES> {
    }

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

    @FunctionalInterface
    public interface MapSource<ENTRIES extends Map<?, ?>> extends Callable<ENTRIES> {
    }

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
