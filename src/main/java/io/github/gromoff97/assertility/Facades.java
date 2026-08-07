package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.BooleanAwait;
import io.github.gromoff97.assertility.api.BooleanTerminals;
import io.github.gromoff97.assertility.api.ComparableAwait;
import io.github.gromoff97.assertility.api.ComparableTerminals;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.ObjectTerminals;
import io.github.gromoff97.assertility.api.OptionalAwait;
import io.github.gromoff97.assertility.api.OptionalTerminals;
import io.github.gromoff97.assertility.api.StringAwait;
import io.github.gromoff97.assertility.api.StringTerminals;
import io.github.gromoff97.assertility.api.TryBooleanAwait;
import io.github.gromoff97.assertility.api.TryComparableAwait;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import io.github.gromoff97.assertility.api.TryOptionalAwait;
import io.github.gromoff97.assertility.api.TryStringAwait;

import java.util.Optional;
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
}
