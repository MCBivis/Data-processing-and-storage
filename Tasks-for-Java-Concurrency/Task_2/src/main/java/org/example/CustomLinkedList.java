package org.example;

import java.util.Iterator;

public class CustomLinkedList implements Iterable<String> {
    private Node head = null;

    public void addFirst(String value) {
        if (head == null) {
            head = new Node(value);
        } else {
            head.lock.lock();
            Node oldHead = head;
            Node node = new Node(value);
            node.next = head;
            head = node;
            oldHead.lock.unlock();
        }
    }

    public void setHead(Node newHead) {
        head = newHead;
    }

    public Node getHead() {
        return head;
    }

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
