package org.example;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

public class XMLParser {
    private Map<String, Person> persons;
    private Person currentPerson;
    private String currentElement;
    private StringBuilder currentText;
    private Map<String, String> currentAttributes;
    private int tempIdCounter;
    private String people;

    public XMLParser() {
        this.persons = new HashMap<>();
        this.currentText = new StringBuilder();
        this.currentAttributes = new HashMap<>();
        this.tempIdCounter = 0;
    }

    public void parseXML(String inputFile) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(inputFile));

        while (reader.hasNext()) {
            int event = reader.next();

            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    handleStartElement(reader);
                    break;

                case XMLStreamConstants.CHARACTERS:
                    currentText.append(reader.getText().trim());
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    handleEndElement(reader);
                    break;
            }
        }
        reader.close();

        // Пост-обработка данных
        postProcessData();

        // Валидация данных
        validateData();
    }

    private void handleStartElement(XMLStreamReader reader) {
        String elementName = reader.getLocalName();
        currentElement = elementName;
        currentText.setLength(0);
        currentAttributes.clear();

        // Сохраняем все атрибуты элемента
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attrName = reader.getAttributeLocalName(i);
            String attrValue = reader.getAttributeValue(i);
            currentAttributes.put(attrName, attrValue);
        }

        switch (elementName) {
            case "person":
                // Пробуем получить id из атрибута
                String id = currentAttributes.get("id");
                if (id == null) {
                    // Если нет id, пробуем создать временный или использовать name
                    id = currentAttributes.get("name");
                    if (id == null) {
                        id = "TEMP_" + (tempIdCounter++);
                    }
                }

                currentPerson = persons.getOrDefault(id, new Person(id));

                // Если person имеет атрибут name, это может быть fullname
                String nameAttr = currentAttributes.get("name");
                if (nameAttr != null && currentPerson.getFirstName() == null) {
                    // Пробуем разбить полное имя на first и last
                    String[] nameParts = nameAttr.split(" ", 2);
                    if (nameParts.length > 0) {
                        currentPerson.setFirstName(nameParts[0]);
                    }
                    if (nameParts.length > 1) {
                        currentPerson.setLastName(nameParts[1]);
                    }
                }

                break;
            case "people":
                this.people = currentAttributes.get("count");
        }
    }

    private void handleEndElement(XMLStreamReader reader) {
        String elementName = reader.getLocalName();
        String text = currentText.toString().trim();
        Person relative;

        if (currentPerson == null) return;

        switch (elementName) {
            case "brother":
                currentPerson.addBrother(text);
                break;

            case "child":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                break;

            case "person":
                persons.put(currentPerson.getId(), currentPerson);
                currentPerson = null;
                break;

            case "id":
                if (!text.isEmpty()) {
                    String newId = text;
                    // Если ID изменился, нужно обновить запись
                    if (!newId.equals(currentPerson.getId())) {
                        Person existingPerson = persons.get(newId);
                        if (existingPerson != null) {
                            // Объединяем данные
                            mergePersons(existingPerson, currentPerson);
                            currentPerson = existingPerson;
                        } else {
                            currentPerson.setId(newId);
                            persons.put(newId, currentPerson);
                            persons.remove(currentPerson.getId()); // удаляем старую запись
                        }
                    }
                }
                break;

            case "firstname":
            case "first":
                if (!text.isEmpty()) {
                    currentPerson.setFirstName(text);
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        currentPerson.setFirstName(value);
                    }
                }
                break;

            case "surname":
            case "family-name":
            case "family":
                if (!text.isEmpty()) {
                    currentPerson.setLastName(text);
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        currentPerson.setLastName(value);
                    }
                }
                break;

            case "fullname":
                // Обрабатываем структурированный fullname
                // Если внутри fullname были first и family, они уже обработаны
                break;

            case "gender":
                if (!text.isEmpty()) {
                    currentPerson.setGender(normalizeGender(text));
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        currentPerson.setGender(normalizeGender(value));
                    }
                }
                break;

            case "spouce":
            case "spouse":
            case "husband":
            case "wife":
                if (!text.isEmpty()) {
                    currentPerson.setSpouse(text);
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        currentPerson.setSpouse(value);
                    }
                }
                break;

            case "children-number":
                if (!text.isEmpty()) {
                    try {
                        currentPerson.setChildrenNumber(Integer.parseInt(text));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid children-number: " + text);
                    }
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        try {
                            currentPerson.setChildrenNumber(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid children-number: " + value);
                        }
                    }
                }
                break;

            case "siblings-number":
                if (!text.isEmpty()) {
                    try {
                        currentPerson.setSiblingsNumber(Integer.parseInt(text));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid siblings-number: " + text);
                    }
                } else {
                    String value = currentAttributes.get("value");
                    if (value != null) {
                        try {
                            currentPerson.setSiblingsNumber(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid siblings-number: " + value);
                        }
                    }
                }
                break;

            case "father":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.addChild(currentPerson);
                relative.setGender("M");
                break;
            case "mother":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.addChild(currentPerson);
                relative.setGender("F");
                break;
            case "parent":
                if (text.isEmpty()) {
                    String value = currentAttributes.get("value");
                    if (value != null && !"UNKNOWN".equals(value)) {
                        relative = persons.getOrDefault(value, new Person(value));
                        currentPerson.addParent(relative);
                        relative.addChild(currentPerson);
                    }
                }
                break;

            case "son":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                relative.setGender("M");
                break;
            case "daughter":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                relative.setGender("F");
                break;

            case "sister":
                String sisterId = currentAttributes.get("id");
                if (sisterId != null) {
                    currentPerson.addSister(sisterId);
                } else if (!text.isEmpty()) {
                    currentPerson.addSister(text);
                }
                break;

            case "siblings":
                String siblingId = currentAttributes.get("val");
                if (siblingId != null) {
                    // Временно сохраняем sibling без определения пола
                    currentPerson.addSibling(siblingId);
                }
                break;
        }
    }

    private void postProcessData() {
        // Обрабатываем временные sibling связи и определяем пол
        for (Person person : persons.values()) {
            // Обрабатываем siblings с неизвестным полом
            for (String siblingId : person.getSiblings()) {
                Person sibling = persons.get(siblingId);
                if (sibling != null) {
                    if ("male".equals(sibling.getGender())) {
                        person.addBrother(siblingId);
                    } else if ("female".equals(sibling.getGender())) {
                        person.addSister(siblingId);
                    } else {
                        // Если пол неизвестен, добавляем в оба списка (как было раньше)
                        person.addBrother(siblingId);
                        person.addSister(siblingId);
                    }
                }
            }
            // Очищаем временный список siblings
            person.clearSiblings();

            // Удаляем дубликаты из всех списков
            person.removeDuplicates();
        }
    }

    private void mergePersons(Person target, Person source) {
        // Объединяем данные из source в target
        if (source.getFirstName() != null) target.setFirstName(source.getFirstName());
        if (source.getLastName() != null) target.setLastName(source.getLastName());
        if (source.getGender() != null) target.setGender(source.getGender());
        if (source.getSpouse() != null) target.setSpouse(source.getSpouse());
        if (source.getChildrenNumber() != null) target.setChildrenNumber(source.getChildrenNumber());
        if (source.getSiblingsNumber() != null) target.setSiblingsNumber(source.getSiblingsNumber());

        for (Person parent : source.getParents()) target.addParent(parent);
        for (Person child : source.getChildren()) target.addChild(child);
        for (String brother : source.getBrothers()) target.addBrother(brother);
        for (String sister : source.getSisters()) target.addSister(sister);
        for (String sibling : source.getSiblings()) target.addSibling(sibling);
    }

    private String normalizeGender(String gender) {
        if (gender == null) return null;

        switch (gender.toUpperCase()) {
            case "M":
            case "MALE":
                return "male";
            case "F":
            case "FEMALE":
                return "female";
            default:
                return gender.toLowerCase();
        }
    }

    private void validateData() {
        int warnings = 0;
        for (Person person : persons.values()) {
            // Проверка количества детей
            if (person.getChildrenNumber() != null &&
                    person.getChildren().size() != person.getChildrenNumber()) {
                if (warnings < 50) { // Ограничим вывод предупреждений
                    System.err.println("Warning: Person " + person.getId() +
                            " children count mismatch. Expected: " + person.getChildrenNumber() +
                            ", Actual: " + person.getChildren().size());
                }
                warnings++;
            }

            // Проверка количества siblings
            if (person.getSiblingsNumber() != null) {
                int totalSiblings = person.getBrothers().size() + person.getSisters().size();
                if (totalSiblings != person.getSiblingsNumber()) {
                    if (warnings < 50) {
                        System.err.println("Warning: Person " + person.getId() +
                                " siblings count mismatch. Expected: " + person.getSiblingsNumber() +
                                ", Actual: " + totalSiblings);
                    }
                    warnings++;
                }
            }
        }
        if (warnings >= 50) {
            System.err.println("... and " + (warnings - 50) + " more warnings");
        }
        System.out.println("Total validation warnings: " + warnings);
    }

    public void writeStructuredXML(String outputFile) throws Exception {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(
                new FileOutputStream(outputFile), "UTF-8");

        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeCharacters("\n");
        writer.writeStartElement("people");
        writer.writeAttribute("count", this.people);
        writer.writeCharacters("\n");

        for (Person person : persons.values()) {
            writePersonElement(writer, person, 1);
            writer.writeCharacters("\n");
        }

        writer.writeEndElement(); // people
        writer.writeCharacters("\n");
        writer.writeEndDocument();
        writer.close();
    }

    private void writePersonElement(XMLStreamWriter writer, Person person, int indent) throws Exception {
        writeIndent(writer, indent);
        writer.writeStartElement("person");
        writer.writeAttribute("id", person.getId());
        writer.writeCharacters("\n");

        writeElement(writer, "id", person.getId(), indent + 1);
        writeElement(writer, "firstname", person.getFirstName(), indent + 1);
        writeElement(writer, "surname", person.getLastName(), indent + 1);
        writeElement(writer, "gender", person.getGender(), indent + 1);
        writeElement(writer, "spouse", person.getSpouse(), indent + 1);

        // Родители
        if (!person.getParents().isEmpty()) {
            writeIndent(writer, indent + 1);
            writer.writeStartElement("parents");
            writer.writeCharacters("\n");
            for (Person parent : person.getParents()) {

                writeElement(writer, "parent", parent.getId(), indent + 2);
            }
            writeIndent(writer, indent + 1);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        // Дети
        if (!person.getChildren().isEmpty()) {
            writeIndent(writer, indent + 1);
            writer.writeStartElement("children");
            writer.writeCharacters("\n");
            for (Person child : person.getChildren()) {
                writeElement(writer, "child", child.getId(), indent + 2);
            }
            writeIndent(writer, indent + 1);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        // Братья
        if (!person.getBrothers().isEmpty()) {
            writeIndent(writer, indent + 1);
            writer.writeStartElement("brothers");
            writer.writeCharacters("\n");
            for (String brother : person.getBrothers()) {
                writeElement(writer, "brother", brother, indent + 2);
            }
            writeIndent(writer, indent + 1);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        // Сестры
        if (!person.getSisters().isEmpty()) {
            writeIndent(writer, indent + 1);
            writer.writeStartElement("sisters");
            writer.writeCharacters("\n");
            for (String sister : person.getSisters()) {
                writeElement(writer, "sister", sister, indent + 2);
            }
            writeIndent(writer, indent + 1);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        writeIndent(writer, indent);
        writer.writeEndElement(); // person
    }

    private void writeElement(XMLStreamWriter writer, String name, String value, int indent) throws Exception {
        if (value != null && !value.isEmpty()) {
            writeIndent(writer, indent);
            writer.writeStartElement(name);
            writer.writeCharacters(value);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
    }

    private void writeIndent(XMLStreamWriter writer, int indent) throws Exception {
        for (int i = 0; i < indent; i++) {
            writer.writeCharacters("  "); // 2 пробела на уровень
        }
    }

    public Map<String, Person> getPersons() {
        return persons;
    }
}