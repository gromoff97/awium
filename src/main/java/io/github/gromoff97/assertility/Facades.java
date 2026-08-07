package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.BooleanAwait;
import io.github.gromoff97.assertility.api.BooleanTerminals;
import io.github.gromoff97.assertility.api.ComparableAwait;
import io.github.gromoff97.assertility.api.ComparableTerminals;
import io.github.gromoff97.assertility.api.CollectionAwait;
import io.github.gromoff97.assertility.api.CollectionTerminals;
import io.github.gromoff97.assertility.api.MapAwait;
import io.github.gromoff97.assertility.api.MapTerminals;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.ObjectTerminals;
import io.github.gromoff97.assertility.api.OptionalAwait;
import io.github.gromoff97.assertility.api.OptionalTerminals;
import io.github.gromoff97.assertility.api.SequencedCollectionAwait;
import io.github.gromoff97.assertility.api.SequencedCollectionTerminals;
import io.github.gromoff97.assertility.api.StringAwait;
import io.github.gromoff97.assertility.api.StringTerminals;
import io.github.gromoff97.assertility.api.TryBooleanAwait;
import io.github.gromoff97.assertility.api.TryComparableAwait;
import io.github.gromoff97.assertility.api.TryCollectionAwait;
import io.github.gromoff97.assertility.api.TryMapAwait;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import io.github.gromoff97.assertility.api.TryOptionalAwait;
import io.github.gromoff97.assertility.api.TrySequencedCollectionAwait;
import io.github.gromoff97.assertility.api.TryStringAwait;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

final class Facades {
    private Facades() {
    }

    static <T> ObjectAwait<T> object(AwaitSpec<T> spec) {
        return new ThrowingObjectFacade<>(spec);
    }

    static <T> TryObjectAwait<T> tryObject(AwaitSpec<T> spec) {
        return new ResultObjectFacade<>(spec);
    }

    static BooleanAwait bool(AwaitSpec<Boolean> spec) {
        return new ThrowingBooleanFacade(spec);
    }

    static TryBooleanAwait tryBool(AwaitSpec<Boolean> spec) {
        return new ResultBooleanFacade(spec);
    }

    static <T extends Comparable<? super T>> ComparableAwait<T> comparable(AwaitSpec<T> spec) {
        return new ThrowingComparableFacade<>(spec);
    }

    static <T extends Comparable<? super T>> TryComparableAwait<T> tryComparable(
            AwaitSpec<T> spec) {
        return new ResultComparableFacade<>(spec);
    }

    static StringAwait string(AwaitSpec<String> spec) {
        return new ThrowingStringFacade(spec);
    }

    static TryStringAwait tryString(AwaitSpec<String> spec) {
        return new ResultStringFacade(spec);
    }

    static <T> OptionalAwait<T> optional(AwaitSpec<Optional<T>> spec) {
        return new ThrowingOptionalFacade<>(spec);
    }

    static <T> TryOptionalAwait<T> tryOptional(AwaitSpec<Optional<T>> spec) {
        return new ResultOptionalFacade<>(spec);
    }

    static <E, C extends Collection<E>> CollectionAwait<E, C> collection(AwaitSpec<C> spec) {
        return new ThrowingCollectionFacade<>(spec);
    }

    static <E, C extends Collection<E>> TryCollectionAwait<E, C> tryCollection(
            AwaitSpec<C> spec) {
        return new ResultCollectionFacade<>(spec);
    }

    static <E, C extends SequencedCollection<E>> SequencedCollectionAwait<E, C>
            sequencedCollection(AwaitSpec<C> spec) {
        return new ThrowingSequencedCollectionFacade<>(spec);
    }

    static <E, C extends SequencedCollection<E>> TrySequencedCollectionAwait<E, C>
            trySequencedCollection(AwaitSpec<C> spec) {
        return new ResultSequencedCollectionFacade<>(spec);
    }

    static <K, V, M extends Map<K, V>> MapAwait<K, V, M> map(AwaitSpec<M> spec) {
        return new ThrowingMapFacade<>(spec);
    }

    static <K, V, M extends Map<K, V>> TryMapAwait<K, V, M> tryMap(AwaitSpec<M> spec) {
        return new ResultMapFacade<>(spec);
    }

    private abstract static class ObjectFacade<T, R> implements ObjectTerminals<T, R> {
        final AwaitSpec<T> spec;

        ObjectFacade(AwaitSpec<T> spec) {
            this.spec = spec;
        }

        abstract R execute(String terminalName, Terminal<T, T> terminal);

        @Override
        public R isNull() {
            return execute("isNull", actual -> {
                assertThat(actual).isNull();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isNotNull() {
            return execute("isNotNull", actual -> {
                AssertJSupport.assertNotNull(actual);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isEqualTo(T expected) {
            return execute("isEqualTo", actual -> {
                AssertJSupport.assertRecursiveEqual(actual, expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isNotEqualTo(T expected) {
            return execute("isNotEqualTo", actual -> {
                AssertJSupport.assertNotNull(actual);
                AssertJSupport.assertRecursiveNotEqual(actual, expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public <V> R returns(V expected, Function<? super T, ? extends V> extractor) {
            Validation.callback(extractor, "extractor");
            return execute("returns", actual -> {
                AssertJSupport.assertNotNull(actual);
                final V extracted;
                try {
                    extracted = extractor.apply(actual);
                } catch (AssertionError error) {
                    throw new CallbackFailure(error);
                }
                AssertJSupport.assertRecursiveEqual(extracted, expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R matches(Predicate<? super T> predicate) {
            Validation.callback(predicate, "predicate");
            return matchesValidated("matches", null, predicate);
        }

        @Override
        public R matches(String description, Predicate<? super T> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return matchesValidated("matches: " + description, description, predicate);
        }

        private R matchesValidated(
                String terminalName, String description, Predicate<? super T> predicate) {
            return execute(terminalName, actual -> {
                AssertJSupport.assertNotNull(actual);
                final boolean matched;
                try {
                    matched = predicate.test(actual);
                } catch (AssertionError error) {
                    throw new CallbackFailure(error);
                }
                var assertion = assertThat(matched);
                if (description != null) {
                    assertion.as(description);
                }
                assertion.isTrue();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R satisfies(Consumer<? super T> assertion) {
            Validation.callback(assertion, "assertion");
            return execute("satisfies", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertion.accept(actual);
                return Evaluation.fixed(actual);
            });
        }
    }

    private static final class ThrowingObjectFacade<T> extends ObjectFacade<T, T>
            implements ObjectAwait<T> {
        ThrowingObjectFacade(AwaitSpec<T> spec) {
            super(spec);
        }

        @Override
        T execute(String terminalName, Terminal<T, T> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public ObjectTerminals<T, T> as(String description) {
            return new ThrowingObjectFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public ObjectTerminals<T, T> as(String format, Object... args) {
            return new ThrowingObjectFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultObjectFacade<T> extends ObjectFacade<T, AwaitResult<T>>
            implements TryObjectAwait<T> {
        ResultObjectFacade(AwaitSpec<T> spec) {
            super(spec);
        }

        @Override
        AwaitResult<T> execute(String terminalName, Terminal<T, T> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class BooleanFacade<R> extends ObjectFacade<Boolean, R>
            implements BooleanTerminals<R> {
        BooleanFacade(AwaitSpec<Boolean> spec) {
            super(spec);
        }

        @Override
        public R isTrue() {
            return execute("isTrue", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isTrue();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isFalse() {
            return execute("isFalse", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isFalse();
                return Evaluation.fixed(actual);
            });
        }
    }

    private static final class ThrowingBooleanFacade extends BooleanFacade<Boolean>
            implements BooleanAwait {
        ThrowingBooleanFacade(AwaitSpec<Boolean> spec) {
            super(spec);
        }

        @Override
        Boolean execute(String terminalName, Terminal<Boolean, Boolean> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public BooleanTerminals<Boolean> as(String description) {
            return new ThrowingBooleanFacade(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public BooleanTerminals<Boolean> as(String format, Object... args) {
            return new ThrowingBooleanFacade(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultBooleanFacade extends BooleanFacade<AwaitResult<Boolean>>
            implements TryBooleanAwait {
        ResultBooleanFacade(AwaitSpec<Boolean> spec) {
            super(spec);
        }

        @Override
        AwaitResult<Boolean> execute(String terminalName, Terminal<Boolean, Boolean> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class ComparableFacade<T extends Comparable<? super T>, R>
            extends ObjectFacade<T, R> implements ComparableTerminals<T, R> {
        ComparableFacade(AwaitSpec<T> spec) {
            super(spec);
        }

        @Override
        public R isGreaterThan(T expected) {
            return execute("isGreaterThan", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isGreaterThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isGreaterThanOrEqualTo(T expected) {
            return execute("isGreaterThanOrEqualTo", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isGreaterThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isLessThan(T expected) {
            return execute("isLessThan", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isLessThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isLessThanOrEqualTo(T expected) {
            return execute("isLessThanOrEqualTo", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isLessThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }
    }

    private static final class ThrowingComparableFacade<T extends Comparable<? super T>>
            extends ComparableFacade<T, T> implements ComparableAwait<T> {
        ThrowingComparableFacade(AwaitSpec<T> spec) {
            super(spec);
        }

        @Override
        T execute(String terminalName, Terminal<T, T> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public ComparableTerminals<T, T> as(String description) {
            return new ThrowingComparableFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public ComparableTerminals<T, T> as(String format, Object... args) {
            return new ThrowingComparableFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultComparableFacade<T extends Comparable<? super T>>
            extends ComparableFacade<T, AwaitResult<T>> implements TryComparableAwait<T> {
        ResultComparableFacade(AwaitSpec<T> spec) {
            super(spec);
        }

        @Override
        AwaitResult<T> execute(String terminalName, Terminal<T, T> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class StringFacade<R> extends ComparableFacade<String, R>
            implements StringTerminals<R> {
        StringFacade(AwaitSpec<String> spec) {
            super(spec);
        }

        @Override
        public R isEmpty() {
            return execute("isEmpty", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isNotEmpty() {
            return execute("isNotEmpty", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isNotEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R contains(CharSequence... values) {
            var validated = Validation.stringFragments(values);
            return execute("contains", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).contains(validated);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R doesNotContain(CharSequence... values) {
            var validated = Validation.stringFragments(values);
            return execute("doesNotContain", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).doesNotContain(validated);
                return Evaluation.fixed(actual);
            });
        }
    }

    private static final class ThrowingStringFacade extends StringFacade<String>
            implements StringAwait {
        ThrowingStringFacade(AwaitSpec<String> spec) {
            super(spec);
        }

        @Override
        String execute(String terminalName, Terminal<String, String> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public StringTerminals<String> as(String description) {
            return new ThrowingStringFacade(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public StringTerminals<String> as(String format, Object... args) {
            return new ThrowingStringFacade(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultStringFacade extends StringFacade<AwaitResult<String>>
            implements TryStringAwait {
        ResultStringFacade(AwaitSpec<String> spec) {
            super(spec);
        }

        @Override
        AwaitResult<String> execute(String terminalName, Terminal<String, String> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class OptionalFacade<T, RS, RV>
            extends ObjectFacade<Optional<T>, RS> implements OptionalTerminals<T, RS, RV> {
        OptionalFacade(AwaitSpec<Optional<T>> spec) {
            super(spec);
        }

        abstract RV executeValue(String terminalName, Terminal<Optional<T>, T> terminal);

        @Override
        public RS isEmpty() {
            return execute("isEmpty", actual -> {
                AssertJSupport.assertNotNull(actual);
                assertThat(actual).isEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RV isPresent() {
            return executeValue("isPresent", actual -> Evaluation.fixed(presentValue(actual)));
        }

        @Override
        public RV isPresent(Predicate<? super T> predicate) {
            Validation.callback(predicate, "predicate");
            return presentMatching("isPresent", null, predicate);
        }

        @Override
        public RV isPresent(String description, Predicate<? super T> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return presentMatching("isPresent: " + description, description, predicate);
        }

        private RV presentMatching(
                String terminalName, String description, Predicate<? super T> predicate) {
            return executeValue(terminalName, actual -> {
                var value = presentValue(actual);
                final boolean matched;
                try {
                    matched = predicate.test(value);
                } catch (AssertionError error) {
                    throw new CallbackFailure(error);
                }
                var assertion = assertThat(matched);
                if (description != null) {
                    assertion.as(description);
                }
                assertion.isTrue();
                return Evaluation.fixed(value);
            });
        }

        @Override
        public RV contains(T expected) {
            return executeValue("contains", actual -> {
                var value = presentValue(actual);
                AssertJSupport.assertRecursiveEqual(value, expected);
                return Evaluation.fixed(value);
            });
        }

        @Override
        public <V> RV contains(V expected, Function<? super T, ? extends V> extractor) {
            Validation.callback(extractor, "extractor");
            return executeValue("contains", actual -> {
                var value = presentValue(actual);
                final V extracted;
                try {
                    extracted = extractor.apply(value);
                } catch (AssertionError error) {
                    throw new CallbackFailure(error);
                }
                AssertJSupport.assertRecursiveEqual(extracted, expected);
                return Evaluation.fixed(value);
            });
        }

        private T presentValue(Optional<T> actual) {
            AssertJSupport.assertNotNull(actual);
            assertThat(actual).isPresent();
            return actual.orElseThrow();
        }
    }

    private static final class ThrowingOptionalFacade<T>
            extends OptionalFacade<T, Optional<T>, T> implements OptionalAwait<T> {
        ThrowingOptionalFacade(AwaitSpec<Optional<T>> spec) {
            super(spec);
        }

        @Override
        Optional<T> execute(
                String terminalName, Terminal<Optional<T>, Optional<T>> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        T executeValue(String terminalName, Terminal<Optional<T>, T> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public OptionalTerminals<T, Optional<T>, T> as(String description) {
            return new ThrowingOptionalFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public OptionalTerminals<T, Optional<T>, T> as(String format, Object... args) {
            return new ThrowingOptionalFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultOptionalFacade<T>
            extends OptionalFacade<T, AwaitResult<Optional<T>>, AwaitResult<T>>
            implements TryOptionalAwait<T> {
        ResultOptionalFacade(AwaitSpec<Optional<T>> spec) {
            super(spec);
        }

        @Override
        AwaitResult<Optional<T>> execute(
                String terminalName, Terminal<Optional<T>, Optional<T>> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }

        @Override
        AwaitResult<T> executeValue(String terminalName, Terminal<Optional<T>, T> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class CollectionFacade<
            E, C extends Collection<E>, RC, RE, RL>
            extends ObjectFacade<C, RC> implements CollectionTerminals<E, C, RC, RE, RL> {
        CollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        abstract RE executeElement(String terminalName, Terminal<C, E> terminal);

        abstract RL executeList(String terminalName, Terminal<C, List<E>> terminal);

        @Override
        public RC isEmpty() {
            return execute("isEmpty", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).isEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC isNotEmpty() {
            return execute("isNotEmpty", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).isNotEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC hasSize(int size) {
            var expected = Validation.size(size);
            return execute("hasSize", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).hasSize(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC hasSizeGreaterThan(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeGreaterThan", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).hasSizeGreaterThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC hasSizeGreaterThanOrEqualTo(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeGreaterThanOrEqualTo", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).hasSizeGreaterThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC hasSizeLessThan(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeLessThan", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).hasSizeLessThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC hasSizeLessThanOrEqualTo(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeLessThanOrEqualTo", actual -> {
                assertCollectionReady(actual);
                assertThat(actual).hasSizeLessThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RE single() {
            return executeElement("single", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.allElements(actual);
                return Evaluation.fixed(CollectionSupport.requireSingle(
                        "single", actual, matches, null));
            });
        }

        @Override
        public RE single(Predicate<? super E> predicate) {
            Validation.callback(predicate, "predicate");
            return singleMatching("single", null, predicate);
        }

        @Override
        public RE single(String description, Predicate<? super E> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return singleMatching("single", description, predicate);
        }

        private RE singleMatching(
                String terminalName, String description, Predicate<? super E> predicate) {
            return executeElement(terminalName, actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, predicate);
                return Evaluation.fixed(CollectionSupport.requireSingle(
                        "single", actual, matches, description));
            });
        }

        @Override
        public <V> RE single(Function<? super E, ? extends V> extractor, V expected) {
            Validation.callback(extractor, "extractor");
            return executeElement("single", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, expected, extractor);
                return Evaluation.fixed(CollectionSupport.requireSingle(
                        "single", actual, matches, null));
            });
        }

        @Override
        public RE any() {
            return executeElement("any", actual -> {
                assertCollectionReady(actual);
                return randomMatch(actual, CollectionSupport.allElements(actual), null);
            });
        }

        @Override
        public RE any(Predicate<? super E> predicate) {
            Validation.callback(predicate, "predicate");
            return anyMatching("any", null, predicate);
        }

        @Override
        public RE any(String description, Predicate<? super E> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return anyMatching("any", description, predicate);
        }

        private RE anyMatching(
                String terminalName, String description, Predicate<? super E> predicate) {
            return executeElement(terminalName, actual -> {
                assertCollectionReady(actual);
                return randomMatch(
                        actual, CollectionSupport.matches(actual, predicate), description);
            });
        }

        @Override
        public <V> RE any(Function<? super E, ? extends V> extractor, V expected) {
            Validation.callback(extractor, "extractor");
            return executeElement("any", actual -> {
                assertCollectionReady(actual);
                return randomMatch(
                        actual, CollectionSupport.matches(actual, expected, extractor), null);
            });
        }

        private Evaluation<E> randomMatch(
                C actual, CollectionSupport.Matches<E> matches, String description) {
            CollectionSupport.requireAny("any", actual, matches, description);
            return new Evaluation<>(() -> matches.elements().get(
                    ThreadLocalRandom.current().nextInt(matches.elements().size())));
        }

        @Override
        public RL exactly(int count, Predicate<? super E> predicate) {
            var expectedCount = Validation.exactCount(count);
            Validation.callback(predicate, "predicate");
            return exactlyMatching(expectedCount, null, predicate);
        }

        @Override
        public RL exactly(
                int count, String description, Predicate<? super E> predicate) {
            var expectedCount = Validation.exactCount(count);
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return exactlyMatching(expectedCount, description, predicate);
        }

        private RL exactlyMatching(
                int count, String description, Predicate<? super E> predicate) {
            return executeList("exactly", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, predicate);
                CollectionSupport.requireExactly("exactly", actual, matches, count, description);
                return Evaluation.fixed(CollectionSupport.unmodifiableSnapshot(matches.elements()));
            });
        }

        @Override
        public <V> RL exactly(
                int count, Function<? super E, ? extends V> extractor, V expected) {
            var expectedCount = Validation.exactCount(count);
            Validation.callback(extractor, "extractor");
            return executeList("exactly", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, expected, extractor);
                CollectionSupport.requireExactly("exactly", actual, matches, expectedCount, null);
                return Evaluation.fixed(CollectionSupport.unmodifiableSnapshot(matches.elements()));
            });
        }

        @Override
        public RC all(Predicate<? super E> predicate) {
            Validation.callback(predicate, "predicate");
            return allMatching(null, predicate);
        }

        @Override
        public RC all(String description, Predicate<? super E> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return allMatching(description, predicate);
        }

        private RC allMatching(String description, Predicate<? super E> predicate) {
            return execute("all", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, predicate);
                CollectionSupport.requireAll(actual, matches, description);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public <V> RC all(Function<? super E, ? extends V> extractor, V expected) {
            Validation.callback(extractor, "extractor");
            return execute("all", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, expected, extractor);
                CollectionSupport.requireAll(actual, matches, null);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC none(Predicate<? super E> predicate) {
            Validation.callback(predicate, "predicate");
            return noneMatching(null, predicate);
        }

        @Override
        public RC none(String description, Predicate<? super E> predicate) {
            Validation.predicateDescription(description);
            Validation.callback(predicate, "predicate");
            return noneMatching(description, predicate);
        }

        private RC noneMatching(String description, Predicate<? super E> predicate) {
            return execute("none", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, predicate);
                CollectionSupport.requireNone(actual, matches, description);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public <V> RC none(Function<? super E, ? extends V> extractor, V expected) {
            Validation.callback(extractor, "extractor");
            return execute("none", actual -> {
                assertCollectionReady(actual);
                var matches = CollectionSupport.matches(actual, expected, extractor);
                CollectionSupport.requireNone(actual, matches, null);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC contains(E... expected) {
            var values = Validation.values(expected, "expected");
            return execute("contains", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertContains(actual, Arrays.asList(values));
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC containsAll(Iterable<? extends E> expected) {
            var values = Validation.iterable(expected, "expected");
            return execute("containsAll", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertContains(actual, values);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC doesNotContain(E... unexpected) {
            var values = Validation.values(unexpected, "unexpected");
            return execute("doesNotContain", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertDoesNotContain(actual, Arrays.asList(values));
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC containsExactlyInAnyOrder(E... expected) {
            var values = Validation.values(expected, "expected");
            return execute("containsExactlyInAnyOrder", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertExactlyInAnyOrder(actual, Arrays.asList(values));
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC containsExactlyInAnyOrderElementsOf(Iterable<? extends E> expected) {
            var values = Validation.iterable(expected, "expected");
            return execute("containsExactlyInAnyOrderElementsOf", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertExactlyInAnyOrder(actual, values);
                return Evaluation.fixed(actual);
            });
        }

        final void assertCollectionReady(C actual) {
            AssertJSupport.assertNotNull(actual);
        }
    }

    private static final class ThrowingCollectionFacade<E, C extends Collection<E>>
            extends CollectionFacade<E, C, C, E, List<E>> implements CollectionAwait<E, C> {
        ThrowingCollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        @Override
        C execute(String terminalName, Terminal<C, C> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        E executeElement(String terminalName, Terminal<C, E> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        List<E> executeList(String terminalName, Terminal<C, List<E>> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public CollectionTerminals<E, C, C, E, List<E>> as(String description) {
            return new ThrowingCollectionFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public CollectionTerminals<E, C, C, E, List<E>> as(
                String format, Object... args) {
            return new ThrowingCollectionFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultCollectionFacade<E, C extends Collection<E>>
            extends CollectionFacade<
                    E, C, AwaitResult<C>, AwaitResult<E>, AwaitResult<List<E>>>
            implements TryCollectionAwait<E, C> {
        ResultCollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        @Override
        AwaitResult<C> execute(String terminalName, Terminal<C, C> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }

        @Override
        AwaitResult<E> executeElement(String terminalName, Terminal<C, E> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }

        @Override
        AwaitResult<List<E>> executeList(String terminalName, Terminal<C, List<E>> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class SequencedCollectionFacade<
            E, C extends SequencedCollection<E>, RC, RE, RL>
            extends CollectionFacade<E, C, RC, RE, RL>
            implements SequencedCollectionTerminals<E, C, RC, RE, RL> {
        SequencedCollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        @Override
        public RC containsExactly(E... expected) {
            var values = Validation.values(expected, "expected");
            return execute("containsExactly", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertExactlyInOrder(actual, Arrays.asList(values));
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public RC containsExactlyElementsOf(Iterable<? extends E> expected) {
            var values = Validation.iterable(expected, "expected");
            return execute("containsExactlyElementsOf", actual -> {
                assertCollectionReady(actual);
                CollectionSupport.assertExactlyInOrder(actual, values);
                return Evaluation.fixed(actual);
            });
        }
    }

    private static final class ThrowingSequencedCollectionFacade<
            E, C extends SequencedCollection<E>>
            extends SequencedCollectionFacade<E, C, C, E, List<E>>
            implements SequencedCollectionAwait<E, C> {
        ThrowingSequencedCollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        @Override
        C execute(String terminalName, Terminal<C, C> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        E executeElement(String terminalName, Terminal<C, E> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        List<E> executeList(String terminalName, Terminal<C, List<E>> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public SequencedCollectionTerminals<E, C, C, E, List<E>> as(String description) {
            return new ThrowingSequencedCollectionFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public SequencedCollectionTerminals<E, C, C, E, List<E>> as(
                String format, Object... args) {
            return new ThrowingSequencedCollectionFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultSequencedCollectionFacade<
            E, C extends SequencedCollection<E>>
            extends SequencedCollectionFacade<
                    E, C, AwaitResult<C>, AwaitResult<E>, AwaitResult<List<E>>>
            implements TrySequencedCollectionAwait<E, C> {
        ResultSequencedCollectionFacade(AwaitSpec<C> spec) {
            super(spec);
        }

        @Override
        AwaitResult<C> execute(String terminalName, Terminal<C, C> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }

        @Override
        AwaitResult<E> executeElement(String terminalName, Terminal<C, E> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }

        @Override
        AwaitResult<List<E>> executeList(String terminalName, Terminal<C, List<E>> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }

    private abstract static class MapFacade<K, V, M extends Map<K, V>, R>
            extends ObjectFacade<M, R> implements MapTerminals<K, V, M, R> {
        MapFacade(AwaitSpec<M> spec) {
            super(spec);
        }

        @Override
        public R isEmpty() {
            return execute("isEmpty", actual -> {
                assertMapReady(actual);
                assertThat(actual).isEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R isNotEmpty() {
            return execute("isNotEmpty", actual -> {
                assertMapReady(actual);
                assertThat(actual).isNotEmpty();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R hasSize(int size) {
            var expected = Validation.size(size);
            return execute("hasSize", actual -> {
                assertMapReady(actual);
                assertThat(actual).hasSize(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R hasSizeGreaterThan(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeGreaterThan", actual -> {
                assertMapReady(actual);
                assertThat(actual).hasSizeGreaterThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R hasSizeGreaterThanOrEqualTo(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeGreaterThanOrEqualTo", actual -> {
                assertMapReady(actual);
                assertThat(actual).hasSizeGreaterThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R hasSizeLessThan(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeLessThan", actual -> {
                assertMapReady(actual);
                assertThat(actual).hasSizeLessThan(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R hasSizeLessThanOrEqualTo(int size) {
            var expected = Validation.size(size);
            return execute("hasSizeLessThanOrEqualTo", actual -> {
                assertMapReady(actual);
                assertThat(actual).hasSizeLessThanOrEqualTo(expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R containsKey(K key) {
            return execute("containsKey", actual -> {
                assertMapReady(actual);
                assertThat(actual.containsKey(key)).isTrue();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R doesNotContainKey(K key) {
            return execute("doesNotContainKey", actual -> {
                assertMapReady(actual);
                assertThat(actual.containsKey(key)).isFalse();
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R containsEntry(K key, V value) {
            return execute("containsEntry", actual -> {
                assertMapReady(actual);
                MapSupport.assertContainsEntry(actual, key, value);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R doesNotContainEntry(K key, V value) {
            return execute("doesNotContainEntry", actual -> {
                assertMapReady(actual);
                MapSupport.assertDoesNotContainEntry(actual, key, value);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
            Validation.callback(expected, "expected");
            return execute("containsAllEntriesOf", actual -> {
                assertMapReady(actual);
                MapSupport.assertContainsAllEntries(actual, expected);
                return Evaluation.fixed(actual);
            });
        }

        @Override
        public R containsExactlyInAnyOrderEntriesOf(
                Map<? extends K, ? extends V> expected) {
            Validation.callback(expected, "expected");
            return execute("containsExactlyInAnyOrderEntriesOf", actual -> {
                assertMapReady(actual);
                MapSupport.assertContainsExactlyEntries(actual, expected);
                return Evaluation.fixed(actual);
            });
        }

        private void assertMapReady(M actual) {
            AssertJSupport.assertNotNull(actual);
        }
    }

    private static final class ThrowingMapFacade<K, V, M extends Map<K, V>>
            extends MapFacade<K, V, M, M> implements MapAwait<K, V, M> {
        ThrowingMapFacade(AwaitSpec<M> spec) {
            super(spec);
        }

        @Override
        M execute(String terminalName, Terminal<M, M> terminal) {
            return PollingCore.await(spec, terminalName, terminal);
        }

        @Override
        public MapTerminals<K, V, M, M> as(String description) {
            return new ThrowingMapFacade<>(spec.describedAs(
                    Validation.literalDescription(description)));
        }

        @Override
        public MapTerminals<K, V, M, M> as(String format, Object... args) {
            return new ThrowingMapFacade<>(spec.describedAs(
                    Validation.formattedDescription(format, args)));
        }
    }

    private static final class ResultMapFacade<K, V, M extends Map<K, V>>
            extends MapFacade<K, V, M, AwaitResult<M>> implements TryMapAwait<K, V, M> {
        ResultMapFacade(AwaitSpec<M> spec) {
            super(spec);
        }

        @Override
        AwaitResult<M> execute(String terminalName, Terminal<M, M> terminal) {
            return PollingCore.tryAwait(spec, terminalName, terminal);
        }
    }
}
