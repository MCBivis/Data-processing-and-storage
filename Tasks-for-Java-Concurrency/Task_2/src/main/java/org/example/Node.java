package org.example;

import java.util.concurrent.locks.ReentrantLock;

public class Node {
    String value;
    Node next;
    final ReentrantLock lock = new ReentrantLock();

    Node(String value) {
        this.value = value;
    }
}
