package io.github.gromoff97.awium.conditions.api

import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.dsl.Await.CollectionSource
import io.github.gromoff97.awium.dsl.Await.CollectionViewSource
import io.github.gromoff97.awium.dsl.Await.MapSource
import io.github.gromoff97.awium.dsl.Await.MapViewSource
import io.github.gromoff97.awium.dsl.Await.OptionalSource
import io.github.gromoff97.awium.dsl.Await.await
import io.github.gromoff97.awium.dsl.Await.tryAwait
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.InputCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.MapValueCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition
import java.util.concurrent.Callable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class SourceContractTest {
    @ParameterizedTest
    @MethodSource("missingSources")
    fun `source definitions reject null before execution`(argument: String, create: Executable) {
        assertEquals("$argument must not be null", assertThrows<NullPointerException>(create::execute).message)
    }

    @ParameterizedTest
    @MethodSource("missingConditions")
    fun `every terminal rejects a missing condition before reading the source`(run: Executable) {
        assertEquals("condition must not be null", assertThrows<NullPointerException>(run::execute).message)
    }

    companion object {
        @JvmStatic
        fun missingSources() = listOf(
            argumentSet("collection delegate", "delegate", Executable { CollectionViewSource<String, List<String>>(null) }),
            argumentSet("map delegate", "delegate", Executable { MapViewSource<String, String, Map<String, String>>(null) }),
            argumentSet("await callable", "source", Executable { await(null as Callable<String>?) }),
            argumentSet("await optional", "source", Executable { await(null as OptionalSource<String>?) }),
            argumentSet("await collection", "source", Executable { await(null as CollectionSource<List<String>>?) }),
            argumentSet("await map", "source", Executable { await(null as MapSource<Map<String, String>>?) }),
            argumentSet("await collection view", "source", Executable { await(null as CollectionViewSource<String, List<String>>?) }),
            argumentSet("await map view", "source", Executable { await(null as MapViewSource<String, String, Map<String, String>>?) }),
            argumentSet("tryAwait callable", "source", Executable { tryAwait(null as Callable<String>?) }),
            argumentSet("tryAwait optional", "source", Executable { tryAwait(null as OptionalSource<String>?) }),
            argumentSet("tryAwait collection", "source", Executable { tryAwait(null as CollectionSource<List<String>>?) }),
            argumentSet("tryAwait map", "source", Executable { tryAwait(null as MapSource<Map<String, String>>?) }),
            argumentSet("tryAwait collection view", "source", Executable { tryAwait(null as CollectionViewSource<String, List<String>>?) }),
            argumentSet("tryAwait map view", "source", Executable { tryAwait(null as MapViewSource<String, String, Map<String, String>>?) }))

        @JvmStatic
        fun missingConditions(): List<Arguments> {
            val source = Callable<String> { error("source must not run") }
            val mapSource = MapSource<Map<String, String>> { error("source must not run") }
            return listOf(
                argumentSet("await typed", Executable { await(source).until(null as Condition<String, String>?) }),
                argumentSet("tryAwait typed", Executable { tryAwait(source).until(null as Condition<String, String>?) }),
                argumentSet("await preserving", Executable { await(source).until(null as PreservingCondition<String>?) }),
                argumentSet("tryAwait preserving", Executable { tryAwait(source).until(null as PreservingCondition<String>?) }),
                argumentSet("await comparison", Executable { await(source).until(null as ComparisonCondition<String>?) }),
                argumentSet("tryAwait comparison", Executable { tryAwait(source).until(null as ComparisonCondition<String>?) }),
                argumentSet("await collected", Executable { await(source).until(null as CollectingCondition<InputCondition<String>>?) }),
                argumentSet("tryAwait collected", Executable { tryAwait(source).until(null as CollectingCondition<InputCondition<String>>?) }),
                argumentSet("await selected", Executable { await(mapSource).until(null as SelectedCondition<Map<*, *>>?) }),
                argumentSet("tryAwait selected", Executable { tryAwait(mapSource).until(null as SelectedCondition<Map<*, *>>?) }),
                argumentSet("await value", Executable { await(mapSource).until(null as MapValueCondition<Map<*, *>>?) }),
                argumentSet("tryAwait value", Executable { tryAwait(mapSource).until(null as MapValueCondition<Map<*, *>>?) }))
        }
    }
}
