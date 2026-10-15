package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Source.CollectionSource;
import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import io.github.gromoff97.awium.Source.OptionalSource;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.util.Objects.requireNonNull;

/**
 * Fluent await facade. One condition returns one value; several compatible conditions capture a list
 * from successive observations. During persistence, only the final stage is re-evaluated.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <ELEMENT> value selected from an optional, collection, or map
 */
public final class Await<OBSERVED, ELEMENT, MAP_VALUE> {

    private final Source<? extends OBSERVED> source;
    private final WaitConfiguration configuration;
    private final LongSupplier clock;
    private final LongConsumer parker;

    private Await(Source<? extends OBSERVED> source) {
        this(requireNonNull(source, "source must not be null"),
                DEFAULT, System::nanoTime, LockSupport::parkNanos);
    }

    public static <VALUE> Await<VALUE, Object, Object> await(Source<VALUE> source) {
        return new Await<>(source);
    }

    public static <VALUE> Await<Optional<VALUE>, VALUE, Object> await(OptionalSource<VALUE> source) {
        return new Await<>(source);
    }

    public static <ELEMENT, VALUES extends Collection<ELEMENT>> Await<VALUES, ELEMENT, Object> await(CollectionSource<VALUES> source) {
        return new Await<>(source);
    }

    public static <ELEMENT, VALUES extends Collection<? extends ELEMENT>>
            Await<VALUES, ELEMENT, Object> await(CollectionViewSource<ELEMENT, VALUES> source) {
        return new Await<>(source);
    }

    public static <KEY, VALUE, ENTRIES extends Map<KEY, VALUE>> Await<ENTRIES, Map.Entry<KEY, VALUE>, VALUE> await(MapSource<ENTRIES> source) {
        return new Await<>(source);
    }

    public static <KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>
            Await<ENTRIES, Map.Entry<? extends KEY, ? extends VALUE>, VALUE> await(MapViewSource<KEY, VALUE, ENTRIES> source) {
        return new Await<>(source);
    }

    private Await(Source<? extends OBSERVED> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        this.source = source;
        this.configuration = configuration;
        this.clock = clock;
        this.parker = parker;
    }

    public Await<OBSERVED, ELEMENT, MAP_VALUE> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    public Await<OBSERVED, ELEMENT, MAP_VALUE> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    public Await<OBSERVED, ELEMENT, MAP_VALUE> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    /**
     * Uses a monotonic nanosecond clock and a wait operation receiving a duration in nanoseconds.
     * Both operations must share the same time base; a wait may return early.
     */
    public Await<OBSERVED, ELEMENT, MAP_VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration,
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    private Await<OBSERVED, ELEMENT, MAP_VALUE> reconfigured(WaitConfiguration configuration) {
        return new Await<>(source, configuration, clock, parker);
    }

    public OBSERVED until(PreservingCondition<? super OBSERVED> condition) {
        return complete(required(condition).preservingSession());
    }

    public <RESULT> RESULT until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return complete(required(condition).newSession());
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return complete(required(condition).<ELEMENT>selectionSession());
    }

    public MAP_VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return complete(required(condition).<MAP_VALUE>selectionSession());
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(PreservingCondition<? super OBSERVED> condition) {
        return capture(required(condition).preservingSession());
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> tryUntil(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return capture(required(condition).newSession());
    }

    public AwaitResult<OBSERVED, ELEMENT> tryUntil(SelectedCondition<? super OBSERVED> condition) {
        return capture(required(condition).<ELEMENT>selectionSession());
    }

    public AwaitResult<OBSERVED, MAP_VALUE> tryUntil(MapValueCondition<? super OBSERVED> condition) {
        return capture(required(condition).<MAP_VALUE>selectionSession());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return until(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <RESULT> List<RESULT> until(Condition<? super OBSERVED, ? extends RESULT> first, Condition<? super OBSERVED, ? extends RESULT> second, Condition<? super OBSERVED, ? extends RESULT>... rest) {
        return until(CapturedEvaluator.<OBSERVED, RESULT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<ELEMENT> until(SelectedCondition<? super OBSERVED> first, SelectedCondition<? super OBSERVED> second, SelectedCondition<? super OBSERVED>... rest) {
        return until(CapturedEvaluator.<OBSERVED, ELEMENT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<MAP_VALUE> until(MapValueCondition<? super OBSERVED> first, MapValueCondition<? super OBSERVED> second, MapValueCondition<? super OBSERVED>... rest) {
        return until(CapturedEvaluator.<OBSERVED, MAP_VALUE>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <RESULT> AwaitResult<OBSERVED, List<RESULT>> tryUntil(Condition<? super OBSERVED, ? extends RESULT> first, Condition<? super OBSERVED, ? extends RESULT> second, Condition<? super OBSERVED, ? extends RESULT>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED, RESULT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<ELEMENT>> tryUntil(SelectedCondition<? super OBSERVED> first, SelectedCondition<? super OBSERVED> second, SelectedCondition<? super OBSERVED>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED, ELEMENT>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<MAP_VALUE>> tryUntil(MapValueCondition<? super OBSERVED> first, MapValueCondition<? super OBSERVED> second, MapValueCondition<? super OBSERVED>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED, MAP_VALUE>captured(first, second, rest));
    }

    private <RESULT> RESULT complete(ConditionSession<? super OBSERVED, ? extends RESULT> session) {
        return complete(new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(),
                session.metadata, configuration);
    }

    static <OBSERVED, RESULT> RESULT complete(WaitCompletion<OBSERVED, RESULT> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return success.result();
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, metadata, configuration);
        switch (failure.reason()) {
            case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
            default -> throw new AwaitExecutionException(failure);
        }
    }

    private <RESULT> AwaitResult<OBSERVED, RESULT> capture(ConditionSession<? super OBSERVED, ? extends RESULT> session) {
        var history = new AttemptHistory<OBSERVED, RESULT>();
        var outcome = new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(history);
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return new AwaitResult.Satisfied<>(history.snapshot(), success.attempt().number(), success.result());
        }
        return new AwaitResult.Failed<>(history.snapshot(), outcome.attempt().number(),
                FailureDiagnostics.failure(outcome, session.metadata, configuration));
    }
}
