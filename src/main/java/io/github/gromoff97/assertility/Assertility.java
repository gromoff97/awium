package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.AwaitFactory;
import io.github.gromoff97.assertility.api.BooleanAwait;
import io.github.gromoff97.assertility.api.ComparableAwait;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.OptionalAwait;
import io.github.gromoff97.assertility.api.StringAwait;
import io.github.gromoff97.assertility.api.TryAwaitFactory;
import io.github.gromoff97.assertility.api.TryBooleanAwait;
import io.github.gromoff97.assertility.api.TryComparableAwait;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import io.github.gromoff97.assertility.api.TryOptionalAwait;
import io.github.gromoff97.assertility.api.TryStringAwait;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

public final class Assertility {
    private Assertility() {
    }

    public static BooleanAwait awaitUntil(AwaitSources.BooleanSource source) {
        return Facades.bool(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T extends Comparable<? super T>> ComparableAwait<T> awaitUntil(
            AwaitSources.ComparableSource<T> source) {
        return Facades.comparable(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static StringAwait awaitUntil(AwaitSources.StringSource source) {
        return Facades.string(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> OptionalAwait<T> awaitUntil(AwaitSources.OptionalSource<T> source) {
        return Facades.optional(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> ObjectAwait<T> awaitUntil(AwaitSources.Source<T> source) {
        return Facades.object(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static TryBooleanAwait tryAwaitUntil(AwaitSources.BooleanSource source) {
        return Facades.tryBool(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T extends Comparable<? super T>> TryComparableAwait<T> tryAwaitUntil(
            AwaitSources.ComparableSource<T> source) {
        return Facades.tryComparable(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static TryStringAwait tryAwaitUntil(AwaitSources.StringSource source) {
        return Facades.tryString(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> TryOptionalAwait<T> tryAwaitUntil(AwaitSources.OptionalSource<T> source) {
        return Facades.tryOptional(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static <T> TryObjectAwait<T> tryAwaitUntil(AwaitSources.Source<T> source) {
        return Facades.tryObject(new AwaitSpec<>(Awaitility.await(), source, null));
    }

    public static AwaitFactory await(ConditionFactory factory) {
        return FactoryStages.throwing(Validation.factory(factory));
    }

    public static TryAwaitFactory tryAwait(ConditionFactory factory) {
        return FactoryStages.result(Validation.factory(factory));
    }
}
