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
       // validateData();
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

                currentPerson = persons.getOrDefault(id.trim(), new Person(id.trim()));

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
        String value;
        String[] parts;

        if (currentPerson == null) return;

        switch (elementName) {
            case "brother":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addBrother(relative);
                relative.addSibling(currentPerson);
                relative.setGender("male");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                break;

            case "child":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                break;

            case "children-number":
                value = currentAttributes.get("value");
                currentPerson.setChildrenNumber(Integer.parseInt(value));
                break;

            case "daughter":
                value = currentAttributes.get("id");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                relative.setGender("female");
                relative.setId(value);
                break;

            case "family":
            case "family-name":
                currentPerson.setLastName(text);
                break;

            case "father":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.addChild(currentPerson);
                relative.setGender("male");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                break;

            case "first":
            case "firstname":
                if (!text.isEmpty()) {
                    currentPerson.setFirstName(text);
                } else {
                    value = currentAttributes.get("value").trim();
                    currentPerson.setFirstName(value);
                }
                break;

            case "gender":
                if (!text.isEmpty()) {
                    currentPerson.setGender(normalizeGender(text));
                } else {
                    value = currentAttributes.get("value");
                    currentPerson.setGender(normalizeGender(value));
                }
                break;

            case "husband":
                value = currentAttributes.get("value");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.setSpouse(relative);
                relative.setSpouse(currentPerson);
                relative.setGender("male");
                relative.setId(value);
                break;

            case "id":
                value = currentAttributes.get("value");
                currentPerson.setId(value);
                break;

            case "mother":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.addChild(currentPerson);
                relative.setGender("female");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                break;

            case "parent":
                value = currentAttributes.get("value");
                if (value != null && !"UNKNOWN".equals(value)) {
                    relative = persons.getOrDefault(value, new Person(value));
                    currentPerson.addParent(relative);
                    relative.addChild(currentPerson);
                    relative.setId(value);
                }
                break;

            case "person":
                persons.put(currentPerson.getId(), currentPerson);
                currentPerson = null;
                break;

            case "siblings":
                value = currentAttributes.get("val");
                if (value != null) {
                    parts = value.split(" ", 2);
                    if (parts.length > 0) {
                        relative = persons.getOrDefault(parts[0], new Person(parts[0]));
                        currentPerson.addSibling(relative);
                        relative.addSibling(currentPerson);
                        relative.setId(parts[0]);
                    }
                    if (parts.length > 1) {
                        relative = persons.getOrDefault(parts[1], new Person(parts[1]));
                        currentPerson.addSibling(relative);
                        relative.addSibling(currentPerson);
                        relative.setId(parts[1]);
                    }
                }
                break;

            case "siblings-number":
                value = currentAttributes.get("value");
                currentPerson.setSiblingsNumber(Integer.parseInt(value));
                break;

            case "sister":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addSister(relative);
                relative.addSibling(currentPerson);
                relative.setGender("female");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                break;

            case "son":
                value = currentAttributes.get("id");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.addChild(relative);
                relative.addParent(currentPerson);
                relative.setGender("male");
                relative.setId(value);
                break;

            case "spouce":
                value = currentAttributes.get("value");
                if (value != null && !"UNKNOWN".equals(value)) {
                    relative = persons.getOrDefault(value.trim(), new Person(value.trim()));
                    currentPerson.setSpouse(relative);
                    relative.setSpouse(currentPerson);
                    parts = value.split(" ", 2);
                    if (parts.length > 0) {
                        relative.setFirstName(parts[0]);
                    }
                    if (parts.length > 1) {
                        relative.setLastName(parts[1]);
                    }
                }
                break;

            case "surname":
                value = currentAttributes.get("value");
                currentPerson.setLastName(value.trim());
                break;

            case "wife":
                value = currentAttributes.get("value");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.setSpouse(relative);
                relative.setSpouse(currentPerson);
                relative.setGender("female");
                relative.setId(value);
                break;
        }
    }

    private void postProcessData() {
        List<String> toRemove = new ArrayList<>();
        for (Person person : persons.values()) {
            if (person.getId().split(" ", 2).length == 1){
                String fullname = person.getFirstName() + " " + person.getLastName();
                if (persons.containsKey(fullname)) {
                    mergePersons(persons.get(fullname), person);
                    toRemove.add(fullname);
                }
            }
        }
        for(String remove : toRemove){
            persons.remove(remove);
        }
        for (Person person : persons.values()) {
            for (Person sibling : person.getSiblings()) {
                if (sibling != null) {
                    if ("male".equals(sibling.getGender())) {
                        person.addBrother(sibling);
                    } else if ("female".equals(sibling.getGender())) {
                        person.addSister(sibling);
                    } else {
                     //   System.out.println(person.getId() + " без пола");
                    }
                }
            }
            // Очищаем временный список siblings
            person.clearSiblings();
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
        for (Person brother : source.getBrothers()) target.addBrother(brother);
        for (Person sister : source.getSisters()) target.addSister(sister);
        for (Person sibling : source.getSiblings()) target.addSibling(sibling);
    }

    private String normalizeGender(String gender) {
        if (gender == null) return null;

        switch (gender.toUpperCase()) {
            case "M":
                return "male";
            case "F":
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
        if (person.getSpouse() != null) {
            writeElement(writer, "spouse", person.getSpouse().getId(), indent + 1);
        }

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
            for (Person brother : person.getBrothers()) {
                writeElement(writer, "brother", brother.getId(), indent + 2);
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
            for (Person sister : person.getSisters()) {
                writeElement(writer, "sister", sister.getId(), indent + 2);
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