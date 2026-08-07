package io.github.gromoff97.assertility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class CollectionSupport {
    private CollectionSupport() {
    }

    static <E> Matches<E> allElements(Collection<E> source) {
        var observed = new ArrayList<>(source);
        return new Matches<>(observed, observed, List.of());
    }

    static <E> Matches<E> matches(
            Collection<E> source, Predicate<? super E> predicate) {
        var matches = new ArrayList<E>();
        var observed = new ArrayList<E>();
        for (var element : source) {
            observed.add(element);
            final boolean matched;
            try {
                matched = predicate.test(element);
            } catch (AssertionError error) {
                throw new CallbackFailure(error);
            }
            if (matched) {
                matches.add(element);
            }
        }
        return new Matches<>(observed, matches, List.of());
    }

    static <E, V> Matches<E> matches(
            Collection<E> source, V expected, Function<? super E, ? extends V> extractor) {
        var matches = new ArrayList<E>();
        var observed = new ArrayList<E>();
        var comparisonFailures = new ArrayList<AssertionError>();
        for (var element : source) {
            observed.add(element);
            final V extracted;
            try {
                extracted = extractor.apply(element);
            } catch (AssertionError error) {
                throw new CallbackFailure(error);
            }
            try {
                AssertJSupport.assertRecursiveEqual(extracted, expected);
                matches.add(element);
            } catch (AssertionError comparisonFailure) {
                comparisonFailures.add(comparisonFailure);
            }
        }
        return new Matches<>(observed, matches, comparisonFailures);
    }

    static <E> E requireSingle(
            String selector, Collection<E> source, Matches<E> matches, String description) {
        requireExactly(selector, source, matches, 1, description);
        return matches.elements().getFirst();
    }

    static <E> void requireAny(
            String selector, Collection<E> source, Matches<E> matches, String description) {
        if (matches.elements().isEmpty()) {
            throw Diagnostics.selectorFailure(
                    selector, source, matches, "at least 1", description);
        }
    }

    static <E> void requireExactly(
            String selector,
            Collection<E> source,
            Matches<E> matches,
            int expectedCount,
            String description) {
        if (matches.elements().size() != expectedCount) {
            throw Diagnostics.selectorFailure(
                    selector, source, matches, Integer.toString(expectedCount), description);
        }
    }

    static <E> List<E> unmodifiableSnapshot(List<E> matches) {
        return Collections.unmodifiableList(new ArrayList<>(matches));
    }

    record Matches<E>(List<E> observed, List<E> elements, List<AssertionError> comparisonFailures) {
    }
}
