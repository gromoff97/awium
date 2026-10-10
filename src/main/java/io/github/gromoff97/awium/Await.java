package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import io.github.gromoff97.awium.Source.CollectionSource;
import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import io.github.gromoff97.awium.Source.OptionalSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.WaitConfiguration.defaults;
import static java.util.Objects.requireNonNull;
import static io.github.gromoff97.awium.ConditionDefinition.required;

/**
 * Fluent await facade. One condition returns one value; several compatible conditions capture a list
 * from successive observations. During persistence, only the final stage is re-evaluated.
 *
 * @param <Observed> complete value returned by the source
 * @param <Element> value selected from an optional, collection, or map
 * @param <Family> phantom source-family marker restricting compatible selection conditions
 */
public final class Await<Observed, Element, Family extends Source<?>> {

    private final Source<? extends Observed> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;

    private Await(Source<? extends Observed> source) {
        this(requireNonNull(source, "source must not be null"),
                defaults(), System::nanoTime, LockSupport::parkNanos);
    }

    public static <Value> Await<Value, Value, Source<?>> await(Source<Value> source) {
        return new Await<>(source);
    }

    public static <Value> Await<Optional<Value>, Value, OptionalSource<?>> await(OptionalSource<Value> source) {
        return new Await<>(source);
    }

    public static <Element, Values extends Collection<Element>> Await<Values, Element, CollectionSource<?>> await(CollectionSource<Values> source) {
        return new Await<>(source);
    }

    public static <Element, Values extends Collection<? extends Element>>
            Await<Values, Element, CollectionSource<?>> await(CollectionViewSource<Element, Values> source) {
        return new Await<>(source);
    }

    public static <Key, Value, Entries extends Map<Key, Value>> Await<Entries, Map.Entry<Key, Value>, MapSource<?>> await(MapSource<Entries> source) {
        return new Await<>(source);
    }

    public static <Key, Value, Entries extends Map<? extends Key, ? extends Value>>
            Await<Entries, Map.Entry<? extends Key, ? extends Value>, MapSource<?>> await(MapViewSource<Key, Value, Entries> source) {
        return new Await<>(source);
    }

    private Await(Source<? extends Observed> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        this.source = source;
        this.configuration = configuration;
        this.clock = clock;
        this.parker = parker;
    }

    public Await<Observed, Element, Family> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    public Await<Observed, Element, Family> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    public Await<Observed, Element, Family> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    /**
     * Uses a monotonic nanosecond clock and a wait operation receiving a duration in nanoseconds.
     * Both operations must share the same time base; a wait may return early.
     */
    public Await<Observed, Element, Family> usingTime(LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration,
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    private Await<Observed, Element, Family> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, configuration, clock, parker);
    }

    public Observed until(PreservingCondition<? super Observed> condition) {
        return complete(required(condition).preservingSession());
    }

    public <Expected extends Observed> Observed until(ExpectedCondition<Expected> condition) {
        return complete(required(condition).preservingSession());
    }

    public <Result extends Observed> Result until(NarrowingCondition<Result> condition) {
        return complete(required(condition).newSession());
    }

    public <Result> Result until(Condition<? super Observed, ? extends Result> condition) {
        return complete(required(condition).newSession());
    }

    public Element until(SelectedCondition<? super Observed, Family> condition) {
        return complete(required(condition).<Element>selectionSession());
    }

    public AwaitResult<Observed, Observed> tryUntil(PreservingCondition<? super Observed> condition) {
        return capture(required(condition).preservingSession());
    }

    public <Expected extends Observed> AwaitResult<Observed, Observed> tryUntil(ExpectedCondition<Expected> condition) {
        return capture(required(condition).preservingSession());
    }

    public <Result extends Observed> AwaitResult<Observed, Result> tryUntil(NarrowingCondition<Result> condition) {
        return capture(required(condition).newSession());
    }

    public <Result> AwaitResult<Observed, Result> tryUntil(Condition<? super Observed, ? extends Result> condition) {
        return capture(required(condition).newSession());
    }

    public AwaitResult<Observed, Element> tryUntil(SelectedCondition<? super Observed, Family> condition) {
        return capture(required(condition).<Element>selectionSession());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Observed> until(Predicate<? super Observed> first, Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return until(CapturedEvaluator.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Observed> until(PreservingCondition<? super Observed> first, PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return until(CapturedEvaluator.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Result> List<Result> until(Condition<? super Observed, ? extends Result> first, Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        return until(CapturedEvaluator.<Observed, Result>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Expected extends Observed> List<Observed> until(ExpectedCondition<? extends Expected> first, ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        return until(CapturedEvaluator.<Observed, Expected>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Element> until(SelectedCondition<? super Observed, Family> first, SelectedCondition<? super Observed, Family> second, SelectedCondition<? super Observed, Family>... rest) {
        return until(CapturedEvaluator.<Observed, Element, Family>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Observed>> tryUntil(Predicate<? super Observed> first, Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return tryUntil(CapturedEvaluator.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Observed>> tryUntil(PreservingCondition<? super Observed> first, PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return tryUntil(CapturedEvaluator.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Result> AwaitResult<Observed, List<Result>> tryUntil(Condition<? super Observed, ? extends Result> first, Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        return tryUntil(CapturedEvaluator.<Observed, Result>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Expected extends Observed> AwaitResult<Observed, List<Observed>> tryUntil(ExpectedCondition<? extends Expected> first, ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        return tryUntil(CapturedEvaluator.<Observed, Expected>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Element>> tryUntil(SelectedCondition<? super Observed, Family> first, SelectedCondition<? super Observed, Family> second, SelectedCondition<? super Observed, Family>... rest) {
        return tryUntil(CapturedEvaluator.<Observed, Element, Family>captured(first, second, rest));
    }

    private <Result> Result complete(ConditionSession<? super Observed, ? extends Result> session) {
        return complete(new WaitEngine<Observed, Result>(configuration, clock, parker, source, session).waitFor(),
                session.metadata, configuration);
    }

    static <Observed, Result> Result complete(WaitCompletion<Observed, Result> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return success.result();
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, metadata, configuration);
        switch (failure.reason()) {
            case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
            default -> throw new AwaitExecutionException(failure);
        }
    }

    private <Result> AwaitResult<Observed, Result> capture(ConditionSession<? super Observed, ? extends Result> session) {
        var history = new AttemptHistory<Observed, Result>();
        var outcome = new WaitEngine<Observed, Result>(configuration, clock, parker, source, session).waitFor(history);
        if (outcome instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return new AwaitResult.Satisfied<>(history.snapshot(), success.attempt().number(), success.result());
        }
        return new AwaitResult.Failed<>(history.snapshot(), outcome.attempt().number(),
                FailureDiagnostics.failure(outcome, session.metadata, configuration));
    }
}
