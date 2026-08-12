package io.github.gromoff97.awium;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProbeContainers {

    private ProbeContainers() {
        throw new AssertionError("Utility class");
    }

    static final class ProbeCollection<E> extends AbstractCollection<E> {
        private final int reportedSize;
        private final RuntimeException sizeFailure;
        int sizeCalls;

        ProbeCollection(int reportedSize) {
            this(reportedSize, null);
        }

        ProbeCollection(RuntimeException sizeFailure) {
            this(0, sizeFailure);
        }

        private ProbeCollection(int reportedSize,
                RuntimeException sizeFailure) {
            this.reportedSize = reportedSize;
            this.sizeFailure = sizeFailure;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return reportedSize;
        }

        @Override
        public Iterator<E> iterator() {
            throw new AssertionError("iterator must not be called");
        }

        @Override
        public String toString() {
            return "probe collection";
        }
    }

    static final class ProbeMap<K, V> extends AbstractMap<K, V> {
        private final RuntimeException sizeFailure;
        int sizeCalls;

        ProbeMap() {
            this(null);
        }

        ProbeMap(RuntimeException sizeFailure) {
            this.sizeFailure = sizeFailure;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return 1;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            throw new AssertionError("entrySet must not be called");
        }

        @Override
        public String toString() {
            return "probe map";
        }
    }

    static final class MembershipCollection<E> extends AbstractCollection<E> {
        private final List<? extends E> elements;
        private final RuntimeException iteratorFailure;
        int iteratorCalls;

        MembershipCollection(List<? extends E> elements) {
            this(elements, null);
        }

        MembershipCollection(RuntimeException iteratorFailure) {
            this(List.of(), iteratorFailure);
        }

        private MembershipCollection(List<? extends E> elements,
                RuntimeException iteratorFailure) {
            this.elements = elements;
            this.iteratorFailure = iteratorFailure;
        }

        @Override
        public int size() {
            throw new AssertionError("size must not be called");
        }

        @Override
        public Iterator<E> iterator() {
            iteratorCalls++;
            if (iteratorFailure != null) {
                throw iteratorFailure;
            }
            @SuppressWarnings("unchecked")
            Iterator<E> iterator = (Iterator<E>) elements.iterator();
            return iterator;
        }

        @Override
        public String toString() {
            return "membership collection";
        }
    }

    static final class EntryMap<K, V> extends AbstractMap<K, V> {
        private final List<Entry<K, V>> entries;
        RuntimeException entrySetFailure;
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

    static final class ProbeEntry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        ProbeEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
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

    static final class GreedyValue {
        private final Set<String> matches;
        int equalsCalls;

        GreedyValue(Set<String> matches) {
            this.matches = matches;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof ExpectedValue expected
                    && matches.contains(expected.value());
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    record ExpectedValue(String value) {}

    static final class ThrowingEquals {
        private final RuntimeException failure;

        ThrowingEquals(RuntimeException failure) {
            this.failure = failure;
        }

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
