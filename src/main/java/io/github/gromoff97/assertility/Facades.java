package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.ObjectTerminals;
import io.github.gromoff97.assertility.api.TryObjectAwait;

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
}
