package io.github.gromoff97.awium;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.CollectionConditions.elementAt;
import static io.github.gromoff97.awium.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.SelectedCondition;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.ProbeContainers.once;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangingContainerSelectionTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void indexedSelectionRetriesWhenTheListIsEmptiedBeforeSelection() throws Exception {
        var values = new ChangingList();
        values.add("old");

        var source = changingSource(values, () -> values.add("ready"));
        var result = tryAwait(source::call).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(elementAt(0));

        assertEquals("ready", result.orThrow());
        verify(source, times(2)).call();
    }

    @ParameterizedTest
    @MethodSource("ends")
    void firstAndLastRetryWhenTheDequeIsEmptiedBeforeSelection(SelectedCondition<SequencedCollection<?>> selection) throws Exception {
        var values = new ChangingDeque();
        values.add("old");

        var source = changingSource(values, () -> values.add("ready"));
        var result = tryAwait(source::call).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(selection);

        assertEquals("ready", result.orThrow());
        verify(source, times(2)).call();
    }

    private static List<SelectedCondition<SequencedCollection<?>>> ends() {
        return List.of(firstElement, lastElement);
    }

    @Test
    void singleRetriesWhenTheQueueIsEmptiedBeforeItsIteratorIsCreated() throws Exception {
        var values = new ChangingQueue();
        values.add("old");

        var source = changingSource(values, () -> values.add("ready"));
        var result = tryAwait(source::call).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).until(singleElement);

        assertEquals("ready", result.orThrow());
        verify(source, times(2)).call();
    }

    @Test
    void singleEntryRetriesWhenTheMapIsEmptiedBeforeItsViewIsObtained() throws Exception {
        var values = new ChangingMap();
        values.put("key", "old");
        var source = changingSource(values, () -> values.put("key", "ready"));
        var result = tryAwait(source::call).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5)).until(singleEntry);

        assertEquals(Map.entry("key", "ready"), result.orThrow());
        verify(source, times(2)).call();
    }

    private static <VALUES> Callable<VALUES> changingSource(VALUES values, Runnable change) throws Exception {
        Callable<VALUES> source = mock();
        when(source.call()).thenReturn(values).thenAnswer(ignored -> {
            change.run();
            return values;
        }).thenReturn(values);
        return source;
    }

    private static final class ChangingQueue extends ConcurrentLinkedQueue<String> {
        private final Runnable change = once(this::clear);

        @Override
        public Iterator<String> iterator() {
            change.run();
            return super.iterator();
        }
    }

    private static final class ChangingDeque extends ConcurrentLinkedDeque<String> {
        private final Runnable change = once(this::clear);

        @Override
        public boolean isEmpty() {
            boolean empty = super.isEmpty();
            change.run();
            return empty;
        }

        @Override
        public Iterator<String> iterator() {
            change.run();
            return super.iterator();
        }

        @Override
        public Iterator<String> descendingIterator() {
            change.run();
            return super.descendingIterator();
        }
    }

    private static final class ChangingList extends CopyOnWriteArrayList<String> {
        private final Runnable change = once(this::clear);

        @Override
        public int size() {
            int size = super.size();
            change.run();
            return size;
        }

        @Override
        public Iterator<String> iterator() {
            change.run();
            return super.iterator();
        }
    }

    private static final class ChangingMap extends ConcurrentHashMap<String, String> {
        private final Runnable change = once(this::clear);

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            change.run();
            return super.entrySet();
        }
    }
}
