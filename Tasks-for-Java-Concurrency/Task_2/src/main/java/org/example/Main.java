package org.example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        CustomLinkedList list = new CustomLinkedList();
        Scanner sc = new Scanner(System.in);

        int numSorters = 2;
        long delayMs = 100;

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
                for (int i = 0; i < line.length(); i += 80) {
                    String part = line.substring(i, Math.min(i + 80, line.length()));
                    list.addFirst(part);
                }
            }
        }
    }
}
