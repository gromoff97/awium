package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PresentCondition;
import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class Await<S> extends AbstractAwait<S, Await<S>> {

    private Await(Source<? extends S> source) {
        super(source);
    }

    public static <T> Await<T> await(Source<T> source) {
        return new Await<>(source);
    }

    public static <T> OptionalAwait<T> await(OptionalSource<T> source) {
        return new OptionalAwait<>(source);
    }

    public static <C extends Collection<?>> StructuralAwait<C> await(CollectionSource<C> source) {
        return new StructuralAwait<>(source, "collection", Collection::size);
    }

    public static <M extends Map<?, ?>> StructuralAwait<M> await(MapSource<M> source) {
        return new StructuralAwait<>(source, "map", Map::size);
    }

    Await(Source<? extends S> source, WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<S> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<S> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }

    public static final class OptionalAwait<T> extends AbstractAwait<Optional<T>, OptionalAwait<T>> {

        OptionalAwait(OptionalSource<T> source) {
            super(source);
        }

        private OptionalAwait(OptionalAwait<T> await, WaitConfiguration configuration) {
            super(await, configuration);
        }

        @Override
        OptionalAwait<T> reconfigured(WaitConfiguration configuration) {
            return new OptionalAwait<>(this, configuration);
        }

        public T until(PresentCondition condition) {
            return complete(requireNonNull(condition, "condition must not be null"), null);
        }

        public T until(PresentCondition.ExplainedCondition condition) {
            var explained = requireNonNull(condition, "condition must not be null");
            return complete(explained.delegate(), explained.explanation());
        }

        private T complete(PresentCondition condition, String explanation) {
            Condition<Optional<?>, Object> delegate = condition.delegate();
            return complete(actual -> {
                Evaluation<Object> evaluation = delegate.evaluate(actual);
                T result = evaluation != null && evaluation.status() == SATISFIED ? actual.orElse(null) : null;
                return replaceSatisfiedResult(evaluation, result);
            }, delegate::description, explanation);
        }
    }

    public static final class StructuralAwait<S> extends AbstractAwait<S, StructuralAwait<S>> {

        private final String subject;
        private final ToIntFunction<? super S> size;

        StructuralAwait(Source<? extends S> source, String subject, ToIntFunction<? super S> size) {
            super(source);
            this.subject = subject;
            this.size = size;
        }

        StructuralAwait(Source<? extends S> source, String subject,
                ToIntFunction<? super S> size, WaitConfiguration configuration,
                LongSupplier clock, LongConsumer parker) {
            super(source, configuration, clock, parker);
            this.subject = subject;
            this.size = size;
        }

        private StructuralAwait(StructuralAwait<S> await, WaitConfiguration configuration) {
            super(await, configuration);
            this.subject = await.subject;
            this.size = await.size;
        }

        @Override
        StructuralAwait<S> reconfigured(WaitConfiguration configuration) {
            return new StructuralAwait<>(this, configuration);
        }

        public S until(StructuralCondition condition) {
            return complete(requireNonNull(condition, "condition must not be null"), null);
        }

        public S until(StructuralCondition.ExplainedCondition condition) {
            var explained = requireNonNull(condition, "condition must not be null");
            return complete(explained.delegate(), explained.explanation());
        }

        private S complete(StructuralCondition condition, String explanation) {
            return complete(actual -> actual == null
                    ? unsatisfied(subject + " was null")
                    : condition.evaluate(size.applyAsInt(actual), actual, subject),
                    () -> condition.description(subject), explanation);
        }
    }
}
