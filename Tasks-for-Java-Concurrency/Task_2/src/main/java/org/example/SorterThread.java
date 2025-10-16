package org.example;

import java.util.concurrent.atomic.AtomicLong;

public class SorterThread extends Thread {
    private final CustomLinkedList list;
    private final long delayMs;
    public static final AtomicLong steps = new AtomicLong(0);

    SorterThread(CustomLinkedList list, long delayMs) {
        this.list = list;
        this.delayMs = delayMs;
    }

    @Override
    public void run() {
        try {
            while (true) {
                bubblePass();
                Thread.sleep(delayMs);
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void bubblePass() throws InterruptedException {
        Node prev = null;

        Node curr = list.getHead();
        if (curr == null) return;

        while (true) {
            Node next = curr.next;
            if (next == null) break;

            if (prev != null) prev.lock.lock();
            curr.lock.lock();
            next.lock.lock();

            if (curr.next != next) {
                next.lock.unlock();
                curr.lock.unlock();
                if (prev != null) prev.lock.unlock();
                return;
            }
            if (prev != null) {
                if (prev.next != curr) {
                    next.lock.unlock();
                    curr.lock.unlock();
                    prev.lock.unlock();
                    return;
                }
            }

            Node oldPrev = prev;
            Node oldCurr = curr;
            if (curr.value.compareTo(next.value) > 0) {
                if (prev == null) {
                    list.setHead(next);
                    curr.next = next.next;
                    next.next = curr;
                } else {
                    prev.next = next;
                    curr.next = next.next;
                    next.next = curr;
                }
                Thread.sleep(delayMs);
                System.out.println(curr.value + " \uD83D\uDD04 " + next.value);
                prev = next;
            } else {
                prev = curr;
                curr = next;
            }

            next.lock.unlock();
            oldCurr.lock.unlock();
            if (oldPrev != null) oldPrev.lock.unlock();

            long step = steps.incrementAndGet();
            if (step % 100 == 0) {
                System.out.println(step + " steps");
            }
            Thread.sleep(delayMs);
        }
    }
}
