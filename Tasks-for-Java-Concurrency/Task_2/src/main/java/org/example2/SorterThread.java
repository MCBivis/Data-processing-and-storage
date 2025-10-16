package org.example2;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class SorterThread extends Thread {
    private final List<String> list;
    private final long delayMs;
    public static final AtomicLong steps = new AtomicLong(0);

    SorterThread(List<String> list, long delayMs) {
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
        synchronized (list) {
            int n = list.size();
            if (n < 2) return;

            for (int i = 0; i < n - 1; i++) {
                String a = list.get(i);
                String b = list.get(i + 1);

                if (a.compareTo(b) > 0) {
                    Collections.swap(list, i, i + 1);
                    Thread.sleep(delayMs);
                    System.out.println(a + " \uD83D\uDD04 " + b);
                }

                long step = steps.incrementAndGet();
                if (step % 100 == 0) {
                    System.out.println(step + " steps");
                }
                Thread.sleep(delayMs);
            }
        }
    }
}
