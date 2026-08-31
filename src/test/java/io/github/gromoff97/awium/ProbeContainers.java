package io.github.gromoff97.awium;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProbeContainers {

    private ProbeContainers() {
        throw new AssertionError("Utility class");
    }

    public static final class ProbeCollection<E> extends AbstractCollection<E> {
        private final int reportedSize;
        private final RuntimeException sizeFailure;
        public int sizeCalls;

        public ProbeCollection(int reportedSize) {
            this(reportedSize, null);
        }

        public ProbeCollection(RuntimeException sizeFailure) {
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

    public static final class ProbeMap<K, V> extends AbstractMap<K, V> {
        private final RuntimeException sizeFailure;
        public int sizeCalls;

        public ProbeMap() {
            this(null);
        }

        public ProbeMap(RuntimeException sizeFailure) {
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

    public static final class MembershipCollection<E> extends AbstractCollection<E> {
        private final List<E> elements;
        private final RuntimeException iteratorFailure;
        public int iteratorCalls;

        public MembershipCollection(List<E> elements) {
            this(elements, null);
        }

        public MembershipCollection(RuntimeException iteratorFailure) {
            this(List.of(), iteratorFailure);
        }

        private MembershipCollection(List<E> elements,
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
            return elements.iterator();
        }

        @Override
        public String toString() {
            return "membership collection";
        }
    }

    public static final class EntryMap<K, V> extends AbstractMap<K, V> {
        private final List<Entry<K, V>> entries;
        public RuntimeException entrySetFailure;
        public int entrySetCalls;

        public EntryMap(List<Entry<K, V>> entries) {
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

    public record ProbeEntry<K, V>(K key, V value) implements Map.Entry<K, V> {

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

    public static final class Directional {
        private final boolean result;
        public int equalsCalls;

        public Directional(boolean result) {
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

    public record GreedyValue(Set<String> matches) {
        @Override
        public boolean equals(Object other) {
            return other instanceof ExpectedValue expected
                    && matches.contains(expected.value());
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    public record ExpectedValue(String value) {}

    public record ThrowingEquals(RuntimeException failure) {
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
