package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.AwaitFactory;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.TryAwaitFactory;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

public final class Assertility {
    private Assertility() {
    }

    public static <T> ObjectAwait<T> awaitUntil(AwaitSources.Source<T> source) {
        return Facades.object(new AwaitSpec<>(Awaitility.await(), source, null));
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
