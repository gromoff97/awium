package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ProbeContainers {

    private ProbeContainers() {}

    static <ELEMENT> Collection<ELEMENT> sizeOnlyCollection(int size) {
        Collection<ELEMENT> collection = mock();
        when(collection.size()).thenReturn(size);
        when(collection.isEmpty()).thenAnswer(ignored -> collection.size() == 0);
        when(collection.iterator()).thenThrow(new AssertionError("iterator must not be called"));
        when(collection.toString()).thenReturn("probe collection");
        return collection;
    }

    static <KEY, VALUE> Map<KEY, VALUE> sizeOnlyMap() {
        Map<KEY, VALUE> map = mock();
        when(map.size()).thenReturn(1);
        when(map.isEmpty()).thenAnswer(ignored -> map.size() == 0);
        when(map.entrySet()).thenThrow(new AssertionError("entrySet must not be called"));
        when(map.toString()).thenReturn("probe map");
        return map;
    }

    static <ELEMENT> Collection<ELEMENT> membershipCollection(List<ELEMENT> elements) {
        Collection<ELEMENT> collection = mock();
        when(collection.size()).thenThrow(new AssertionError("size must not be called"));
        when(collection.isEmpty()).thenAnswer(ignored -> collection.size() == 0);
        when(collection.iterator()).thenAnswer(ignored -> elements.iterator());
        when(collection.toString()).thenReturn("membership collection");
        return collection;
    }

    static final class EntryMap<K, V> extends AbstractMap<K, V> {
        private final List<Entry<K, V>> entries;
        RuntimeException entrySetFailure;
        RuntimeException iteratorFailure;
        int entrySetCalls;

        EntryMap(List<Entry<K, V>> entries) {
            this.entries = entries;
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            entrySetCalls++;
            if (entrySetFailure != null) {
                throw entrySetFailure;
            }
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<K, V>> iterator() {
                    if (iteratorFailure != null) throw iteratorFailure;
                    return entries.iterator();
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }

        @Override
        public String toString() {
            return "entry map";
        }
    }

    static final class ProbeEntry<K, V> extends AbstractMap.SimpleImmutableEntry<K, V> {
        ProbeEntry(K key, V value) {
            super(key, value);
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("entry equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("entry hashCode must not be called");
        }
    }

    static final class Directional {
        private final boolean result;
        int equalsCalls;

        Directional(boolean result) {
            this.result = result;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return result;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    record ThrowingEquals(RuntimeException failure) {
        @Override
        public boolean equals(Object other) {
            if (failure != null) {
                throw failure;
            }
            return other instanceof ThrowingEquals;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }
}
