package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.condition.AwaitCondition;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.internal.diagnostics.FailureFactory;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.condition.Condition.ExpectedCondition;
import io.github.gromoff97.awium.condition.Condition.NarrowingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.internal.engine.WaitEngine;

import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.CollectionViewSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.MapViewSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.metadata;
import static java.util.Objects.requireNonNull;

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
    private final WaitEngine engine;

    private Await(Source<? extends Observed> source) {
        this(requireNonNull(source, "source must not be null"),
                new WaitEngine(defaults(), System::nanoTime, LockSupport::parkNanos));
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

    private Await(Source<? extends Observed> source, WaitEngine engine) {
        this.source = source;
        this.engine = engine;
    }

    public Await<Observed, Element, Family> every(Duration interval) {
        return reconfigured(engine.configuration().withEvery(interval));
    }

    public Await<Observed, Element, Family> upTo(Duration timeout) {
        return reconfigured(engine.configuration().withUpTo(timeout));
    }

    public Await<Observed, Element, Family> persisting(Duration persistence) {
        return reconfigured(engine.configuration().withPersistence(persistence));
    }

    /**
     * Uses a monotonic nanosecond clock and a wait operation receiving a duration in nanoseconds.
     * Both operations must share the same time base; a wait may return early.
     */
    public Await<Observed, Element, Family> usingTime(LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, new WaitEngine(engine.configuration(),
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null")));
    }

    private Await<Observed, Element, Family> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, new WaitEngine(configuration, engine.clock(), engine.parker()));
    }

    public Observed until(PreservingCondition<? super Observed> condition) {
        return complete(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Expected extends Observed> Observed until(ExpectedCondition<Expected> condition) {
        return complete(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Result extends Observed> Result until(NarrowingCondition<Result> condition) {
        return complete(ConditionRuntime.evaluator(condition), condition);
    }

    public <Result> Result until(Condition<? super Observed, ? extends Result> condition) {
        return complete(ConditionRuntime.evaluator(condition), condition);
    }

    public Element until(SelectedCondition<? super Observed, Family> condition) {
        return complete(ConditionRuntime.evaluator(condition), condition);
    }

    public AwaitResult<Observed, Observed> tryUntil(PreservingCondition<? super Observed> condition) {
        return capture(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Expected extends Observed> AwaitResult<Observed, Observed> tryUntil(ExpectedCondition<Expected> condition) {
        return capture(ConditionRuntime.preservingEvaluator(condition), condition);
    }

    public <Result extends Observed> AwaitResult<Observed, Result> tryUntil(NarrowingCondition<Result> condition) {
        return capture(ConditionRuntime.evaluator(condition), condition);
    }

    public <Result> AwaitResult<Observed, Result> tryUntil(Condition<? super Observed, ? extends Result> condition) {
        return capture(ConditionRuntime.evaluator(condition), condition);
    }

    public AwaitResult<Observed, Element> tryUntil(SelectedCondition<? super Observed, Family> condition) {
        return capture(ConditionRuntime.evaluator(condition), condition);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Observed> until(Predicate<? super Observed> first, Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return until(ConditionRuntime.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Observed> until(PreservingCondition<? super Observed> first, PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return until(ConditionRuntime.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Result> List<Result> until(Condition<? super Observed, ? extends Result> first, Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        return until(ConditionRuntime.<Observed, Result>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Expected extends Observed> List<Observed> until(ExpectedCondition<? extends Expected> first, ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        return until(ConditionRuntime.<Observed, Expected>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<Element> until(SelectedCondition<? super Observed, Family> first, SelectedCondition<? super Observed, Family> second, SelectedCondition<? super Observed, Family>... rest) {
        return until(ConditionRuntime.<Observed, Element, Family>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Observed>> tryUntil(Predicate<? super Observed> first, Predicate<? super Observed> second, Predicate<? super Observed>... rest) {
        return tryUntil(ConditionRuntime.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Observed>> tryUntil(PreservingCondition<? super Observed> first, PreservingCondition<? super Observed> second, PreservingCondition<? super Observed>... rest) {
        return tryUntil(ConditionRuntime.<Observed>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Result> AwaitResult<Observed, List<Result>> tryUntil(Condition<? super Observed, ? extends Result> first, Condition<? super Observed, ? extends Result> second, Condition<? super Observed, ? extends Result>... rest) {
        return tryUntil(ConditionRuntime.<Observed, Result>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <Expected extends Observed> AwaitResult<Observed, List<Observed>> tryUntil(ExpectedCondition<? extends Expected> first, ExpectedCondition<? extends Expected> second, ExpectedCondition<? extends Expected>... rest) {
        return tryUntil(ConditionRuntime.<Observed, Expected>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<Observed, List<Element>> tryUntil(SelectedCondition<? super Observed, Family> first, SelectedCondition<? super Observed, Family> second, SelectedCondition<? super Observed, Family>... rest) {
        return tryUntil(ConditionRuntime.<Observed, Element, Family>captured(first, second, rest));
    }

    private <Result> Result complete(Function<? super Observed, ? extends ConditionEvaluation<? extends Result>> evaluator,
            AwaitCondition condition) {
        return FailureFactory.complete(engine.waitFor(source, evaluator), metadata(condition), engine.configuration());
    }

    private <Result> AwaitResult<Observed, Result> capture(Function<? super Observed,
            ? extends ConditionEvaluation<? extends Result>> evaluator, AwaitCondition condition) {
        return FailureFactory.capture(engine.recordedWaitFor(source, evaluator), metadata(condition), engine.configuration());
    }
}
