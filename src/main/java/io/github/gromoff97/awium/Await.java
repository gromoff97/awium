package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Entry point for configuring a wait from an ordinary, optional, collection, or map source. */
public final class Await {

    private Await() {}

    /** Configures a wait for an observed value. Container lambdas select a specialized overload. */
    public static <VALUE> ValueAwait<VALUE> await(Callable<? extends VALUE> source) {
        return new ValueAwait<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures an optional wait, retaining the contained value type. */
    public static <VALUE> SelectionAwait<Optional<VALUE>, VALUE> await(OptionalSource<VALUE> source) {
        return new SelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a collection wait, retaining its complete type and element type. */
    public static <ELEMENT, VALUES extends Collection<ELEMENT>> SelectionAwait<VALUES, ELEMENT> await(CollectionSource<VALUES> source) {
        return new SelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a map wait, retaining its complete type, entry type, and value type. */
    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> MapAwait<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new MapAwait<>(source, WaitConfiguration.DEFAULT);
    }

    /** Configures a wait with explicit bounds for a covariant collection source. */
    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            SelectionAwait<VALUES, ELEMENT> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new SelectionAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    /** Configures a wait with explicit bounds for a covariant map source. */
    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            MapAwait<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new MapAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
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
