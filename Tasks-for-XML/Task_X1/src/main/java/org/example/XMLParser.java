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
    private Map<String, String> IDs;
    private Person currentPerson;
    private StringBuilder currentText;
    private Map<String, String> currentAttributes;
    private int tempIdCounter;
    private Integer peopleCounter;

    public XMLParser() {
        this.persons = new HashMap<>();
        this.IDs = new HashMap<>();
        this.currentText = new StringBuilder();
        this.currentAttributes = new HashMap<>();
        this.tempIdCounter = 0;
        this.peopleCounter = 0;
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
        currentText.setLength(0);
        currentAttributes.clear();

        // Сохраняем все атрибуты элемента
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attrName = reader.getAttributeLocalName(i);
            String attrValue = reader.getAttributeValue(i);
            currentAttributes.put(attrName, attrValue);
        }

        if (elementName.equals("person")) {
            // Пробуем получить id из атрибута
            String id = currentAttributes.get("id");
            if (id == null) {
                // Если нет id, пробуем создать временный или использовать name
                id = normalizeSpaces(currentAttributes.get("name"));
                if (id == null) {
                    id = "TEMP_" + (tempIdCounter++);
                }
            }

            currentPerson = persons.getOrDefault(id, new Person(id));

            // Если person имеет атрибут name, это может быть fullname
            String nameAttr = normalizeSpaces(currentAttributes.get("name"));
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
        }
    }

    private void handleEndElement(XMLStreamReader reader) {
        String elementName = reader.getLocalName();
        String text = normalizeSpaces(currentText.toString());
        Person relative;
        String value;
        String[] parts;

        if (currentPerson == null) return;

        switch (elementName) {
            case "brother":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addBrother(relative);
                relative.setGender("male");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "child":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addChild(relative);
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "children-number":
                value = currentAttributes.get("value");
                currentPerson.setChildrenNumber(Integer.parseInt(value));
                break;

            case "daughter":
                value = currentAttributes.get("id");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.addChild(relative);
                relative.setGender("female");
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "family":
            case "family-name":
                currentPerson.setLastName(text);
                break;

            case "father":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.setGender("male");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                persons.putIfAbsent(relative.getId(), relative);
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
                relative.setGender("male");
                relative.setId(value);
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "id":
                value = currentAttributes.get("value");
                currentPerson.setId(value);
                break;

            case "mother":
                relative = persons.getOrDefault(text, new Person(text));
                currentPerson.addParent(relative);
                relative.setGender("female");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "parent":
                value = currentAttributes.get("value");
                if (value != null && !"UNKNOWN".equals(value)) {
                    relative = persons.getOrDefault(value, new Person(value));
                    currentPerson.addParent(relative);
                    relative.setId(value);
                    persons.putIfAbsent(relative.getId(), relative);
                }
                if ("UNKNOWN".equals(value)){
                    currentPerson.incUnknownParents();
                }
                break;

            case "person":
                if (currentPerson.getId().contains("TEMP")){
                    String fullname = currentPerson.getFirstName() + " " + currentPerson.getLastName();
                    currentPerson.setId(fullname);
                }
                if (persons.containsKey(currentPerson.getId())){
                    mergePersons(persons.get(currentPerson.getId()), currentPerson);
                }else {
                    persons.put(currentPerson.getId(), currentPerson);
                }
                currentPerson = null;
                break;

            case "siblings":
                value = normalizeSpaces(currentAttributes.get("val"));
                if (value != null) {
                    parts = value.split(" ");
                    for (String part : parts) {
                        relative = persons.getOrDefault(part, new Person(part));
                        currentPerson.addSibling(relative);
                        relative.setId(part);
                        persons.putIfAbsent(relative.getId(), relative);
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
                relative.setGender("female");
                parts = text.split(" ", 2);
                if (parts.length > 0) {
                    relative.setFirstName(parts[0]);
                }
                if (parts.length > 1) {
                    relative.setLastName(parts[1]);
                }
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "son":
                value = currentAttributes.get("id");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.addChild(relative);
                relative.setGender("male");
                relative.setId(value);
                persons.putIfAbsent(relative.getId(), relative);
                break;

            case "spouce":
                value = normalizeSpaces(currentAttributes.get("value"));
                if (value != null && !"NONE".equals(value)) {
                    relative = persons.getOrDefault(value, new Person(value));
                    currentPerson.setSpouse(relative);
                    parts = value.split(" ", 2);
                    if (parts.length > 0) {
                        relative.setFirstName(parts[0]);
                    }
                    if (parts.length > 1) {
                        relative.setLastName(parts[1]);
                    }
                    persons.putIfAbsent(relative.getId(), relative);
                }
                break;

            case "surname":
                value = normalizeSpaces(currentAttributes.get("value"));
                currentPerson.setLastName(value);
                break;

            case "wife":
                value = currentAttributes.get("value");
                relative = persons.getOrDefault(value, new Person(value));
                currentPerson.setSpouse(relative);
                relative.setGender("female");
                relative.setId(value);
                persons.putIfAbsent(relative.getId(), relative);
                break;
        }
    }

    private void postProcessData() {
        List<String> toRemove = new ArrayList<>();
        for (Person person : persons.values()) {
            if (person.getId().split(" ", 2).length == 1){
                String fullname = person.getFirstName() + " " + person.getLastName();
                IDs.put(fullname, person.getId());
                if (persons.containsKey(fullname)) {
                    mergePersons(person, persons.get(fullname));
                    toRemove.add(fullname);
                }
            }
        }
        for(String remove : toRemove){
            persons.remove(remove);
        }
        for (Person person : persons.values()) {
            peopleCounter++;
            if(person.getSpouse() != null) {
                if (person.getSpouse().getId().split(" ", 2).length == 2) {
                    person.getSpouse().setId(IDs.get(person.getSpouse().getId()));
                }
            }
            for (Person sister : person.getSisters()) {
                if (sister.getId().split(" ", 2).length == 2) {
                    sister.setId(IDs.get(sister.getId()));
                }
            }
            for (Person brother : person.getBrothers()) {
                if (brother.getId().split(" ", 2).length == 2) {
                    brother.setId(IDs.get(brother.getId()));
                }
            }
            for (Person sibling : person.getSiblings()) {
                if (sibling.getId().split(" ", 2).length == 2) {
                    sibling.setId(IDs.get(sibling.getId()));
                }
            }
            for (Person child : person.getChildren()) {
                if (child.getId().split(" ", 2).length == 2) {
                    child.setId(IDs.get(child.getId()));
                }
            }
            for (Person parent : person.getParents()) {
                if (parent.getId().split(" ", 2).length == 2) {
                    parent.setId(IDs.get(parent.getId()));
                }
            }
            for (Person sibling : person.getSiblings()) {
                if (sibling != null) {
                    String gender = sibling.getGender();
                    if (gender == null){
                        gender = persons.get(sibling.getId()).getGender();
                    }
                    if ("male".equals(gender)) {
                        person.addBrother(sibling);
                    } else if ("female".equals(gender)) {
                        person.addSister(sibling);
                    } else {
                        System.err.println(sibling.getId() + " без пола");
                    }
                }
            }
            // Очищаем временный список siblings
            person.clearSiblings();
            person.removeDublicatedElements();
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
        for (int i=0; i<source.getUnknownParents() && target.getUnknownParents() < 2; i++) {
            target.incUnknownParents();
        }

        for (Person parent : source.getParents()){
            if (!target.getSiblings().contains(parent) && !target.getBrothers().contains(parent) && !target.getSisters().contains(parent)) {
                target.addParent(parent);
            }
        }
        for (Person child : source.getChildren()) {
            if (child != target) {
                target.addChild(child);
            }
        }
        for (Person brother : source.getBrothers()){
            if (!target.getParents().contains(brother)) {
                target.addBrother(brother);
            }
        }
        for (Person sister : source.getSisters()){
            if (!target.getParents().contains(sister)) {
                target.addSister(sister);
            }
        }
        for (Person sibling : source.getSiblings()){
            if (!target.getParents().contains(sibling)) {
                target.addSibling(sibling);
            }
        }
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

    private String normalizeSpaces(String name) {
        if (name == null) return null;
        return name.trim().replaceAll("\\s+", " ");
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
        writer.writeAttribute("count", peopleCounter.toString());
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

        writeElement(writer, "firstname", person.getFirstName(), indent + 1);
        writeElement(writer, "lastname", person.getLastName(), indent + 1);
        writeElement(writer, "gender", person.getGender(), indent + 1);
        if (person.getSpouse() != null) {
            String gender = person.getSpouse().getGender();

            if (gender == null){
                gender = persons.get(person.getSpouse().getId()).getGender();
            }
            if ("male".equals(gender)) {
                writeElement(writer, "husband", person.getSpouse().getId(), indent + 1);
            } else if ("female".equals(gender)) {
                writeElement(writer, "wife", person.getSpouse().getId(), indent + 1);
            } else {
                System.err.println(person.getSpouse().getId() + " без пола");
            }
        }

        // Родители
        if (!person.getParents().isEmpty()) {
            writeIndent(writer, indent + 1);
            writer.writeStartElement("parents");
            writer.writeCharacters("\n");
            for (Person parent : person.getParents()) {
                String gender = parent.getGender();
                if (gender == null){
                    gender = persons.get(parent.getId()).getGender();
                }
                if ("male".equals(gender)) {
                    writeElement(writer, "father", parent.getId(), indent + 2);
                } else if ("female".equals(gender)) {
                    writeElement(writer, "mother", parent.getId(), indent + 2);
                } else {
                    System.err.println(parent.getId() + " без пола");
                }
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
                String gender = child.getGender();
                if (gender == null){
                    gender = persons.get(child.getId()).getGender();
                }
                if ("male".equals(gender)) {
                    writeElement(writer, "son", child.getId(), indent + 2);
                } else if ("female".equals(gender)) {
                    writeElement(writer, "daughter", child.getId(), indent + 2);
                } else {
                    System.err.println(child.getId() + " без пола");
                }
            }
            writeIndent(writer, indent + 1);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        if (!person.getBrothers().isEmpty() || !person.getSisters().isEmpty()) {
            // Братья
            writeIndent(writer, indent + 1);
            writer.writeStartElement("siblings");
            writer.writeCharacters("\n");
            for (Person brother : person.getBrothers()) {
                writeElement(writer, "brother", brother.getId(), indent + 2);
            }
            // Сестры
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