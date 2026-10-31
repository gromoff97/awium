package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public final class Await {

    private Await() {}

    public static <VALUE> ValueAwait<VALUE> await(Callable<? extends VALUE> source) {
        return new ValueAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <VALUE> SelectionAwait<Optional<VALUE>, VALUE> await(OptionalSource<VALUE> source) {
        return new SelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<ELEMENT>> SelectionAwait<VALUES, ELEMENT> await(CollectionSource<VALUES> source) {
        return new SelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> MapAwait<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new MapAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            SelectionAwait<VALUES, ELEMENT> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new SelectionAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            MapAwait<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new MapAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    public static <VALUE> DiagnosticAwait<VALUE> tryAwait(Callable<? extends VALUE> source) {
        return new DiagnosticAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <VALUE> DiagnosticSelectionAwait<Optional<VALUE>, VALUE> tryAwait(OptionalSource<VALUE> source) {
        return new DiagnosticSelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<ELEMENT>> DiagnosticSelectionAwait<VALUES, ELEMENT> tryAwait(CollectionSource<VALUES> source) {
        return new DiagnosticSelectionAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> DiagnosticMapAwait<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> tryAwait(MapSource<ENTRIES> source) {
        return new DiagnosticMapAwait<>(source, WaitConfiguration.DEFAULT);
    }

    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            DiagnosticSelectionAwait<VALUES, ELEMENT> tryAwait(CollectionViewSource<ELEMENT, VALUES> source) {
        return new DiagnosticSelectionAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            DiagnosticMapAwait<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> tryAwait(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new DiagnosticMapAwait<>(requireNonNull(source, "source must not be null").delegate(), WaitConfiguration.DEFAULT);
    }

    @FunctionalInterface
    public interface OptionalSource<VALUE> extends Callable<Optional<VALUE>> {}

    @FunctionalInterface
    public interface CollectionSource<VALUES extends Collection<?>> extends Callable<VALUES> {}

    @FunctionalInterface
    public interface MapSource<ENTRIES extends Map<?, ?>> extends Callable<ENTRIES> {}

    public record CollectionViewSource<ELEMENT, VALUES extends Collection<? extends ELEMENT>>(
            Callable<? extends VALUES> delegate) {

        public CollectionViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }
    }

    public record MapViewSource<KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>(
            Callable<? extends ENTRIES> delegate) {

        public MapViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }
    }
}
