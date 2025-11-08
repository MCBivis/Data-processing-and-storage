package org.example;

import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception{
        String input = "people.xml";
        String output = "output.xml";

        Set<String> elementNames = ElementNames.GetAllNames(input);

        System.out.println("Всего уникальных элементов: " + elementNames.size());
        System.out.println("Список элементов:");
        elementNames.stream().sorted().forEach(System.out::println);
        XMLParser parser = new XMLParser();
        parser.parseXML(input);
        parser.writeStructuredXML(output);
    }
}