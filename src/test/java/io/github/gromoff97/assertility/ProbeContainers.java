package io.github.gromoff97.assertility;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
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
}
