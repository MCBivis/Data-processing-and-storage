package org.example;

import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

// ==== Узел списка ====
class Node {
    String value;
    Node next;
    final ReentrantLock lock = new ReentrantLock();

    Node(String value) {
        this.value = value;
    }
}

// ==== Сам список ====
class CustomLinkedList implements Iterable<String> {
    Node head;

    // Добавление строки в начало
    public void addFirst(String value) {
        if (head == null) {
            head = new Node(value);
        }else {
            Node oldHead = head;
            oldHead.lock.lock();
            try {
                Node node = new Node(value);
                node.next = head;
                head = node;
            } finally {
                oldHead.lock.unlock();
            }
        }
    }

    // Установка нового head (нужно для сортировки)
    public void setHead(Node newHead) {
        Node oldHead = head;
        oldHead.lock.lock();
        try {
            head = newHead;
        } finally {
            oldHead.lock.unlock();
        }
    }

    public Node getHead() {
        return head;
    }

    // Итератор для вывода в for-each
    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            Node current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public String next() {
                String val = current.value;
                current = current.next;
                return val;
            }
        };
    }
}

// ==== Поток сортировки (бесконечный пузырёк) ====
class SorterThread extends Thread {
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
        } catch (InterruptedException ignored) {}
    }

    private void bubblePass() throws InterruptedException {
        if (list.head == null) return;
        Node prev = null;

        // безопасно читаем голову под headLock
        list.head.lock.lock();
        Node curr = list.getHead();
        list.head.lock.unlock();

        while (curr.next != null) {
            Node next = curr.next;

            try {
                // Захватываем локи в порядке слева -> справа
                curr.lock.lock();
                next.lock.lock();


                // Если нужно — меняем соседние узлы местами (swap)
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
                }
            } finally {
                // Разблокируем именно те локи, которые захватывали
                next.lock.unlock();
                curr.lock.unlock();
            }

            prev = curr;
            curr = next;

            Thread.sleep(delayMs);
        }
    }
}

// ==== Главная программа ====
public class Main {
    public static void main(String[] args) throws Exception {
        CustomLinkedList list = new CustomLinkedList();
        Scanner sc = new Scanner(System.in);

        int numSorters = 1;   // количество потоков сортировки
        long delayMs = 1000;  // задержка между шагами (мс)

        for (int i = 0; i < numSorters; i++) {
            new SorterThread(list, delayMs).start();
        }

        System.out.println("Введите строки (пустая строка — показать список):");
        while (true) {
            String line = sc.nextLine();
            if (line.isEmpty()) {
                System.out.println("=== Текущее состояние списка ===");
                for (String s : list) {
                    System.out.println(s);
                }
                System.out.println("-----");
            } else {
                // Разбиваем длинные строки (>80 символов)
                for (int i = 0; i < line.length(); i += 80) {
                    String part = line.substring(i, Math.min(i + 80, line.length()));
                    list.addFirst(part);
                }
            }
        }
    }
}
