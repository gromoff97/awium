package io.github.gromoff97.awium.conditions

import java.util.AbstractMap.SimpleImmutableEntry
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

object ProbeContainers {
    @JvmStatic
    fun once(action: Runnable): Runnable {
        val once: Runnable = mock()
        doAnswer { action.run(); null }.doNothing().`when`(once).run()
        return once
    }

    @JvmStatic
    fun <ELEMENT> sizeOnlyCollection(size: Int): Collection<ELEMENT> {
        val collection: Collection<ELEMENT> = mock()
        `when`(collection.size).thenReturn(size)
        `when`(collection.isEmpty()).thenAnswer { collection.size == 0 }
        `when`(collection.iterator()).thenThrow(AssertionError("iterator must not be called"))
        `when`(collection.toString()).thenReturn("probe collection")
        return collection
    }

    @JvmStatic
    fun <KEY, VALUE> sizeOnlyMap(): Map<KEY, VALUE> {
        val map: Map<KEY, VALUE> = mock()
        `when`(map.size).thenReturn(1)
        `when`(map.isEmpty()).thenAnswer { map.size == 0 }
        `when`(map.entries).thenThrow(AssertionError("entrySet must not be called"))
        `when`(map.toString()).thenReturn("probe map")
        return map
    }

    @JvmStatic
    fun <ELEMENT> membershipCollection(elements: List<ELEMENT>): MutableCollection<ELEMENT> {
        val collection: MutableCollection<ELEMENT> = mock()
        `when`(collection.size).thenThrow(AssertionError("size must not be called"))
        `when`(collection.isEmpty()).thenAnswer { collection.size == 0 }
        `when`(collection.iterator()).thenAnswer { elements.iterator() }
        `when`(collection.toString()).thenReturn("membership collection")
        return collection
    }

    class EntryMap<KEY, VALUE>(private val sourceEntries: List<Map.Entry<KEY, VALUE>>) : AbstractMap<KEY, VALUE>() {
        @JvmField var beforeEntrySet = Runnable {}
        @JvmField var beforeIterator = Runnable {}
        @JvmField var entrySetCalls = 0

        override fun isEmpty() = sourceEntries.isEmpty()

        override val entries: Set<Map.Entry<KEY, VALUE>>
            get() {
                entrySetCalls++
                beforeEntrySet.run()
                return object : AbstractSet<Map.Entry<KEY, VALUE>>() {
                    override val size get() = sourceEntries.size

                    override fun iterator(): Iterator<Map.Entry<KEY, VALUE>> {
                        beforeIterator.run()
                        return sourceEntries.iterator()
                    }
                }
            }

        override fun toString() = "entry map"
    }

    class ProbeEntry<KEY, VALUE>(key: KEY, value: VALUE) : SimpleImmutableEntry<KEY, VALUE>(key, value) {
        override fun equals(other: Any?): Boolean = throw AssertionError("entry equals must not be called")
        override fun hashCode(): Int = throw AssertionError("entry hashCode must not be called")
    }
}
