package io.github.confidesconch.villagelife.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class DueQueueTest {

    @Test
    void returnsOnlyKeysThatAreDue() {
        DueQueue<String> queue = new DueQueue<>();
        queue.add("a", 10);
        queue.add("b", 20);
        assertTrue(queue.pollDue(5).isEmpty());
        assertEquals(Optional.of("a"), queue.pollDue(10));
        assertTrue(queue.pollDue(15).isEmpty());
        assertEquals(Optional.of("b"), queue.pollDue(25));
        assertEquals(0, queue.size());
    }

    @Test
    void repeatedChangesKeepTheEarlierDueTick() {
        DueQueue<String> queue = new DueQueue<>();
        queue.add("house", 10);
        queue.add("house", 30); // another change later must not postpone the scan
        assertEquals(1, queue.size());
        assertEquals(Optional.of("house"), queue.pollDue(10));
    }

    @Test
    void removedKeysAreNotReturned() {
        DueQueue<String> queue = new DueQueue<>();
        queue.add("gone", 1);
        queue.remove("gone");
        assertTrue(queue.pollDue(100).isEmpty());
    }
}
