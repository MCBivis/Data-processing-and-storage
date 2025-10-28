package org.example;

import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception{
        String path = "people.xml";

        Set<String> elementNames = ElementNames.GetAllNames(path);

        System.out.println("Всего уникальных элементов: " + elementNames.size());
        System.out.println("Список элементов:");
        elementNames.stream().sorted().forEach(System.out::println);
    }
}