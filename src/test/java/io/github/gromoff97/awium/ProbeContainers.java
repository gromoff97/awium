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

    static final class ProbeCollection<E> extends AbstractCollection<E> {
        private final int size;
        private final RuntimeException sizeFailure;
        int sizeCalls;

        ProbeCollection(int size) {
            this(size, null);
        }

        ProbeCollection(RuntimeException sizeFailure) {
            this(0, sizeFailure);
        }

        private ProbeCollection(int size, RuntimeException sizeFailure) {
            this.size = size;
            this.sizeFailure = sizeFailure;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return size;
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
        private final int size;
        private final RuntimeException sizeFailure;
        int sizeCalls;

        ProbeMap(int size) {
            this(size, null);
        }

        ProbeMap(RuntimeException sizeFailure) {
            this(0, sizeFailure);
        }

        private ProbeMap(int size, RuntimeException sizeFailure) {
            this.size = size;
            this.sizeFailure = sizeFailure;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return size;
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
        private final Collection<? extends E> elements;
        private final RuntimeException iteratorFailure;
        private final int failingNext;
        private final RuntimeException nextFailure;
        int iteratorCalls;
        int hasNextCalls;
        int nextCalls;

        MembershipCollection(Collection<? extends E> elements) {
            this(elements, null, 0, null);
        }

        MembershipCollection(RuntimeException iteratorFailure) {
            this(List.of(), iteratorFailure, 0, null);
        }

        MembershipCollection(Collection<? extends E> elements, int failingNext,
                RuntimeException nextFailure) {
            this(elements, null, failingNext, nextFailure);
        }

        private MembershipCollection(Collection<? extends E> elements,
                RuntimeException iteratorFailure, int failingNext,
                RuntimeException nextFailure) {
            this.elements = elements;
            this.iteratorFailure = iteratorFailure;
            this.failingNext = failingNext;
            this.nextFailure = nextFailure;
        }

        @Override
        public int size() {
            throw new AssertionError("size must not be called");
        }

        @Override
        public boolean isEmpty() {
            throw new AssertionError("isEmpty must not be called");
        }

        @Override
        public Iterator<E> iterator() {
            iteratorCalls++;
            if (iteratorFailure != null) {
                throw iteratorFailure;
            }
            Iterator<? extends E> delegate = elements.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    hasNextCalls++;
                    return delegate.hasNext();
                }

                @Override
                public E next() {
                    nextCalls++;
                    if (nextFailure != null && nextCalls == failingNext) {
                        throw nextFailure;
                    }
                    return delegate.next();
                }
            };
        }

        @Override
        public boolean contains(Object value) {
            throw new AssertionError("contains must not be called");
        }

        @Override
        public boolean containsAll(Collection<?> values) {
            throw new AssertionError("containsAll must not be called");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return "membership collection";
        }
    }

    static final class ExpectedCollection<E> extends AbstractCollection<E> {
        private final Collection<E> elements;
        private final RuntimeException isEmptyFailure;
        int isEmptyCalls;

        ExpectedCollection(Collection<E> elements) {
            this(elements, null);
        }

        ExpectedCollection(RuntimeException isEmptyFailure) {
            this(List.of(), isEmptyFailure);
        }

        private ExpectedCollection(Collection<E> elements,
                RuntimeException isEmptyFailure) {
            this.elements = elements;
            this.isEmptyFailure = isEmptyFailure;
        }

        @Override
        public int size() {
            throw new AssertionError("size must not be called");
        }

        @Override
        public boolean isEmpty() {
            isEmptyCalls++;
            if (isEmptyFailure != null) {
                throw isEmptyFailure;
            }
            return elements.isEmpty();
        }

        @Override
        public Iterator<E> iterator() {
            throw new AssertionError("iterator must not be called");
        }
    }

    static final class EntryMap<K, V> extends AbstractMap<K, V> {
        private final List<Entry<K, V>> entries;
        RuntimeException sizeFailure;
        RuntimeException isEmptyFailure;
        RuntimeException entrySetFailure;
        RuntimeException iteratorFailure;
        RuntimeException nextFailure;
        int failingNext;
        int sizeCalls;
        int isEmptyCalls;
        int entrySetCalls;
        int iteratorCalls;
        int hasNextCalls;
        int nextCalls;

        EntryMap(List<Entry<K, V>> entries) {
            this.entries = entries;
        }

        @Override
        public int size() {
            sizeCalls++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return entries.size();
        }

        @Override
        public boolean isEmpty() {
            isEmptyCalls++;
            if (isEmptyFailure != null) {
                throw isEmptyFailure;
            }
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
                    iteratorCalls++;
                    if (iteratorFailure != null) {
                        throw iteratorFailure;
                    }
                    Iterator<Entry<K, V>> delegate = entries.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            hasNextCalls++;
                            return delegate.hasNext();
                        }

                        @Override
                        public Entry<K, V> next() {
                            nextCalls++;
                            if (nextFailure != null
                                    && nextCalls == failingNext) {
                                throw nextFailure;
                            }
                            return delegate.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }

        @Override
        public boolean containsKey(Object key) {
            throw new AssertionError("containsKey must not be called");
        }

        @Override
        public V get(Object key) {
            throw new AssertionError("get must not be called");
        }

        @Override
        public boolean containsValue(Object value) {
            throw new AssertionError("containsValue must not be called");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return "entry map";
        }
    }

    static final class ProbeEntry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;
        RuntimeException keyFailure;
        RuntimeException valueFailure;
        int keyCalls;
        int valueCalls;

        ProbeEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            keyCalls++;
            if (keyFailure != null) {
                throw keyFailure;
            }
            return key;
        }

        @Override
        public V getValue() {
            valueCalls++;
            if (valueFailure != null) {
                throw valueFailure;
            }
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
        final boolean equalsResult;
        int equalsCalls;

        Directional(boolean equalsResult) {
            this.equalsResult = equalsResult;
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof Directional && equalsResult;
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }
    }

    static final class GreedyValue {
        final Set<String> matches;
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

    record ExpectedValue(String value) {
    }

    static final class ThrowingEquals {
        final RuntimeException failure;

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
