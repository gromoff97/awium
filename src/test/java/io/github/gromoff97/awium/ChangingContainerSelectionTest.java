package io.github.gromoff97.awium;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangingContainerSelectionTest {

    @Test
    void indexedSelectionRetriesWhenTheListIsEmptiedBeforeSelection() {
        var values = new ChangingList();
        values.add("old");
        int[] calls = {0};
        var time = new FakeTime(0);

        var result = await(() -> {
            if (++calls[0] == 2) values.add("ready");
            return values;
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .tryUntil(CollectionConditions.elementAt(0));

        assertEquals("ready", result.orThrow());
        assertEquals(2, calls[0]);
    }

    @Test
    void firstAndLastRetryWhenTheDequeIsEmptiedBeforeSelection() {
        for (boolean last : new boolean[] {false, true}) {
            var values = new ChangingDeque();
            values.add("old");
            int[] calls = {0};
            var time = new FakeTime(0);

            var result = await(() -> {
                if (++calls[0] == 2) values.add("ready");
                return values;
            }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                    .tryUntil(last ? CollectionConditions.lastElement : CollectionConditions.firstElement);

            assertEquals("ready", result.orThrow());
            assertEquals(2, calls[0]);
        }
    }

    @Test
    void singleRetriesWhenTheQueueIsEmptiedBeforeItsIteratorIsCreated() {
        var values = new ChangingQueue();
        values.add("old");
        int[] calls = {0};
        var time = new FakeTime(0);

        var result = await(() -> {
            if (++calls[0] == 2) values.add("ready");
            return values;
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).tryUntil(CollectionConditions.singleElement);

        assertEquals("ready", result.orThrow());
        assertEquals(2, calls[0]);
    }

    @Test
    void singleEntryRetriesWhenTheMapIsEmptiedBeforeItsViewIsObtained() {
        var values = new ChangingMap();
        values.put("key", "old");
        int[] calls = {0};
        var time = new FakeTime(0);
        var result = await(() -> {
            if (++calls[0] == 2) values.put("key", "ready");
            return values;
        }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).tryUntil(MapConditions.singleEntry);

        assertEquals(Map.entry("key", "ready"),
                result.orThrow());
        assertEquals(2, calls[0]);
    }

    private static final class ChangingQueue extends ConcurrentLinkedQueue<String> {
        private boolean first = true;

        @Override
        public Iterator<String> iterator() {
            if (first) {
                first = false;
                clear();
            }
            return super.iterator();
        }
    }

    private static final class ChangingDeque extends ConcurrentLinkedDeque<String> {
        private boolean first = true;

        private void change() {
            if (first) {
                first = false;
                clear();
            }
        }

        @Override
        public boolean isEmpty() {
            boolean empty = super.isEmpty();
            change();
            return empty;
        }

        @Override
        public Iterator<String> iterator() {
            change();
            return super.iterator();
        }

        @Override
        public Iterator<String> descendingIterator() {
            change();
            return super.descendingIterator();
        }
    }

    private static final class ChangingList extends CopyOnWriteArrayList<String> {
        private boolean first = true;

        private void change() {
            if (first) {
                first = false;
                clear();
            }
        }

        @Override
        public int size() {
            int size = super.size();
            change();
            return size;
        }

        @Override
        public Iterator<String> iterator() {
            change();
            return super.iterator();
        }
    }

    private static final class ChangingMap extends ConcurrentHashMap<String, String> {
        private boolean first = true;

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            if (first) {
                first = false;
                clear();
            }
            return super.entrySet();
        }
    }
}
