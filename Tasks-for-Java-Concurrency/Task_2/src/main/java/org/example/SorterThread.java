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
        if (list.head == null) return;
        Node prev = null;

        Node curr = list.getHead();

        while (curr.next != null) {
            Node next = curr.next;

            try {
                curr.lock.lock();
                next.lock.lock();

                if (curr.value.compareTo(next.value) > 0 && curr.next == next) {
                    if (prev == null) {
                        list.setHead(next);
                        curr.next = next.next;
                        next.next = curr;
                    } else {
                        prev.next = next;
                        curr.next = next.next;
                        next.next = curr;
                    }
                    System.out.println(curr.value);
                    System.out.println(next.value);
                }
            } finally {
                next.lock.unlock();
                curr.lock.unlock();
            }

            prev = curr;
            curr = next;

            Thread.sleep(delayMs);
        }
    }
}
