package io.github.gromoff97.assertility;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ProbeContainers {

    private ProbeContainers() {
    }

    static final class ProbeCollection<E> extends AbstractCollection<E> {
        private final int size;
        private final RuntimeException sizeFailure;
        int sizeCalls;
        int isEmptyCalls;
        int iteratorCalls;

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
        public boolean isEmpty() {
            isEmptyCalls++;
            return false;
        }

        @Override
        public Iterator<E> iterator() {
            iteratorCalls++;
            return Collections.emptyIterator();
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
        int isEmptyCalls;
        int entrySetCalls;

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
        public boolean isEmpty() {
            isEmptyCalls++;
            return false;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            entrySetCalls++;
            return Set.of();
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
        int sizeCalls;
        int isEmptyCalls;
        int iteratorCalls;
        int hasNextCalls;
        int nextCalls;
        int containsCalls;
        int containsAllCalls;
        int equalsCalls;
        int hashCodeCalls;

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
            sizeCalls++;
            return elements.size();
        }

        @Override
        public boolean isEmpty() {
            isEmptyCalls++;
            return elements.isEmpty();
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
            containsCalls++;
            throw new AssertionError("contains must not be called");
        }

        @Override
        public boolean containsAll(Collection<?> values) {
            containsAllCalls++;
            throw new AssertionError("containsAll must not be called");
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            hashCodeCalls++;
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            return "membership collection";
        }
    }

    static final class ExpectedCollection<E> extends AbstractCollection<E> {
        private final Collection<? extends E> elements;
        private final RuntimeException isEmptyFailure;
        int sizeCalls;
        int isEmptyCalls;
        int iteratorCalls;

        ExpectedCollection(Collection<? extends E> elements) {
            this(elements, null);
        }

        ExpectedCollection(RuntimeException isEmptyFailure) {
            this(List.of(), isEmptyFailure);
        }

        private ExpectedCollection(Collection<? extends E> elements,
                RuntimeException isEmptyFailure) {
            this.elements = elements;
            this.isEmptyFailure = isEmptyFailure;
        }

        @Override
        public int size() {
            sizeCalls++;
            return elements.size();
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
            iteratorCalls++;
            Iterator<? extends E> delegate = elements.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public E next() {
                    return delegate.next();
                }
            };
        }
    }
}
