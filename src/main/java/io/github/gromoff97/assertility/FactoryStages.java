package io.github.gromoff97.assertility;

import io.github.gromoff97.assertility.api.AwaitFactory;
import io.github.gromoff97.assertility.api.ObjectAwait;
import io.github.gromoff97.assertility.api.TryAwaitFactory;
import io.github.gromoff97.assertility.api.TryObjectAwait;
import org.awaitility.core.ConditionFactory;

final class FactoryStages {
    private FactoryStages() {
    }

    static AwaitFactory throwing(ConditionFactory factory) {
        return new ThrowingFactory(factory);
    }

    static TryAwaitFactory result(ConditionFactory factory) {
        return new ResultFactory(factory);
    }

    private record ThrowingFactory(ConditionFactory factory) implements AwaitFactory {
        @Override
        public <T> ObjectAwait<T> until(AwaitSources.Source<T> source) {
            return Facades.object(new AwaitSpec<>(factory, source, null));
        }
    }

    private record ResultFactory(ConditionFactory factory) implements TryAwaitFactory {
        @Override
        public <T> TryObjectAwait<T> until(AwaitSources.Source<T> source) {
            return Facades.tryObject(new AwaitSpec<>(factory, source, null));
        }
    }
}
