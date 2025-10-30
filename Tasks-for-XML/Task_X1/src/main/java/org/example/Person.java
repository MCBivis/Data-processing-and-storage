package org.example;

import java.util.*;
import java.util.Objects;

public class Person {
    private String id;
    private String firstName;
    private String lastName;
    private String gender;
    private String spouse;
    private Set<Person> parents;
    private Set<Person> children;
    private Set<String> brothers;
    private Set<String> sisters;
    private Set<String> siblings; // Временное хранение siblings с неизвестным полом
    private Integer childrenNumber;
    private Integer siblingsNumber;

    public Person(String id) {
        this.id = id;
        this.parents = new HashSet<>();
        this.children = new HashSet<>();
        this.brothers = new HashSet<>();
        this.sisters = new HashSet<>();
        this.siblings = new HashSet<>();
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getSpouse() { return spouse; }
    public void setSpouse(String spouse) { this.spouse = spouse; }

    public List<Person> getParents() { return new ArrayList<>(parents); }
    public void addParent(Person parentId) { parents.add(parentId); }

    public List<Person> getChildren() { return new ArrayList<>(children); }
    public void addChild(Person childId) { children.add(childId); }

    public List<String> getBrothers() { return new ArrayList<>(brothers); }
    public void addBrother(String brotherId) { brothers.add(brotherId); }

    public List<String> getSisters() { return new ArrayList<>(sisters); }
    public void addSister(String sisterId) { sisters.add(sisterId); }

    public List<String> getSiblings() { return new ArrayList<>(siblings); }
    public void addSibling(String siblingId) { siblings.add(siblingId); }
    public void clearSiblings() { siblings.clear(); }

    public Integer getChildrenNumber() { return childrenNumber; }
    public void setChildrenNumber(Integer childrenNumber) { this.childrenNumber = childrenNumber; }

    public Integer getSiblingsNumber() { return siblingsNumber; }
    public void setSiblingsNumber(Integer siblingsNumber) { this.siblingsNumber = siblingsNumber; }

    public void removeDuplicates() {
        // HashSet автоматически удаляет дубликаты
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(id, person.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}