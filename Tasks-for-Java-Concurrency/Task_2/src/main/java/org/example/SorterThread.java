package org.example;

public class SorterThread extends Thread {
    private final CustomLinkedList list;
    private final long delayMs;

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
            try {
                if (prev != null) {
                    prev.lock.lock();
                }
                curr.lock.lock();
                next.lock.lock();

                if (curr.next != next) return;
                if (prev != null) {
                    if (prev.next != curr) return;
                }

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
                    System.out.println(curr.value + " \uD83D\uDD04 " + next.value);
                }
            } finally {
                next.lock.unlock();
                curr.lock.unlock();
                if (prev != null) {
                    prev.lock.unlock();
                }
            }

            prev = curr;
            curr = next;

            Thread.sleep(delayMs);
        }
    }
}
