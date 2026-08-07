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
        return new Matches<>(observed, observed, List.of(), List.of());
    }

    static <E> Matches<E> matches(
            Collection<E> source, Predicate<? super E> predicate) {
        var matches = new ArrayList<E>();
        var nonMatches = new ArrayList<E>();
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
            } else {
                nonMatches.add(element);
            }
        }
        return new Matches<>(observed, matches, nonMatches, List.of());
    }

    static <E, V> Matches<E> matches(
            Collection<E> source, V expected, Function<? super E, ? extends V> extractor) {
        var matches = new ArrayList<E>();
        var nonMatches = new ArrayList<E>();
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
                nonMatches.add(element);
                comparisonFailures.add(comparisonFailure);
            }
        }
        return new Matches<>(observed, matches, nonMatches, comparisonFailures);
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

    static <E> void requireAll(
            Collection<E> source, Matches<E> matches, String description) {
        if (source.isEmpty()) {
            var message = new StringBuilder(
                    "expected a non-empty collection whose elements all match");
            if (description != null) {
                message.append(System.lineSeparator())
                        .append("description: ").append(description);
            }
            throw new AssertionError(message.toString());
        }
        if (matches.elements().size() != source.size()) {
            throw Diagnostics.quantifierFailure("all", source, matches, description);
        }
    }

    static <E> void requireNone(
            Collection<E> source, Matches<E> matches, String description) {
        if (!matches.elements().isEmpty()) {
            throw Diagnostics.quantifierFailure("none", source, matches, description);
        }
    }

    static <E> void assertContains(Collection<E> actual, List<? extends E> expected) {
        if (!containsEveryExpectedValue(actual, expected)) {
            throw new AssertionError("collection did not recursively contain all expected elements");
        }
    }

    static <E> void assertDoesNotContain(
            Collection<E> actual, List<? extends E> unexpected) {
        for (var unexpectedElement : unexpected) {
            for (var actualElement : actual) {
                if (recursivelyEqual(actualElement, unexpectedElement)) {
                    throw new AssertionError(
                            "collection recursively contained an unexpected element");
                }
            }
        }
    }

    static <E> void assertExactlyInAnyOrder(
            Collection<E> actual, List<? extends E> expected) {
        if (!canConsumeExpectedExactly(actual, expected)) {
            throw new AssertionError(
                    "collection did not recursively contain exactly the expected elements in any order");
        }
    }

    static <E> void assertExactlyInOrder(
            Collection<E> actual, List<? extends E> expected) {
        if (actual.size() != expected.size()) {
            throw new AssertionError(
                    "collection size differed from the ordered expected content");
        }
        var actualIterator = actual.iterator();
        var expectedIterator = expected.iterator();
        while (actualIterator.hasNext()) {
            if (!recursivelyEqual(actualIterator.next(), expectedIterator.next())) {
                throw new AssertionError(
                        "collection order or recursively compared content differed");
            }
        }
    }

    private static <E> boolean containsEveryExpectedValue(
            Collection<E> actual, List<? extends E> expected) {
        for (var expectedElement : expected) {
            var found = false;
            for (var actualElement : actual) {
                if (recursivelyEqual(actualElement, expectedElement)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static <E> boolean canConsumeExpectedExactly(
            Collection<E> actual, List<? extends E> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        var actualSnapshot = new ArrayList<>(actual);
        var consumed = new boolean[actualSnapshot.size()];
        for (var expectedElement : expected) {
            var found = false;
            for (var index = 0; index < actualSnapshot.size(); index++) {
                if (!consumed[index]
                        && recursivelyEqual(actualSnapshot.get(index), expectedElement)) {
                    consumed[index] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean recursivelyEqual(Object actual, Object expected) {
        try {
            AssertJSupport.assertRecursiveEqual(actual, expected);
            return true;
        } catch (AssertionError mismatch) {
            return false;
        }
    }

    record Matches<E>(
            List<E> observed,
            List<E> elements,
            List<E> nonMatches,
            List<AssertionError> comparisonFailures) {
    }
}
