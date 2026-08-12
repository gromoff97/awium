package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.structural;
import static java.util.Objects.requireNonNull;

public final class StructuralAwait<S> extends AbstractAwait<S, StructuralAwait<S>> {

    private final String subject;
    private final ToIntFunction<? super S> size;

    public StructuralAwait(Source<? extends S> source, String subject,
            ToIntFunction<? super S> size) {
        super(source);
        this.subject = requireNonNull(subject);
        this.size = requireNonNull(size);
    }

    StructuralAwait(Source<? extends S> source, String subject,
            ToIntFunction<? super S> size, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
        this.subject = requireNonNull(subject);
        this.size = requireNonNull(size);
    }

    private StructuralAwait(StructuralAwait<S> await,
            WaitConfiguration configuration) {
        super(await, configuration);
        this.subject = await.subject;
        this.size = await.size;
    }

    @Override
    StructuralAwait<S> reconfigured(
            WaitConfiguration configuration) {
        return new StructuralAwait<>(this, configuration);
    }

    public S until(StructuralCondition condition) {
        return complete(structural(
                requireNonNull(condition, "condition must not be null"),
                subject, size));
    }

    public S until(StructuralCondition.ExplainedCondition condition) {
        return complete(structural(
                requireNonNull(condition, "condition must not be null"),
                subject, size));
    }
}
