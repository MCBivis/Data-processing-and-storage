package org.example;

import jakarta.xml.bind.annotation.*;
import java.util.*;

@XmlRootElement(name = "person")
@XmlAccessorType(XmlAccessType.FIELD)
public class Person {
    @XmlID
    @XmlAttribute(name = "id")
    private String id;

    @XmlElement
    private String firstName;

    @XmlElement
    private String lastName;

    @XmlElement
    private String gender;

    @XmlTransient
    private Person spouse;

    @XmlIDREF
    @XmlElement
    private Person husband;

    @XmlIDREF
    @XmlElement
    private Person wife;

    @XmlIDREF
    @XmlElement
    private Person father;

    @XmlIDREF
    @XmlElement
    private Person mother;

    @XmlTransient
    private Set<Person> parents = new HashSet<>();

    @XmlTransient
    private Set<Person> children = new HashSet<>();

    @XmlElementWrapper(name = "sons")
    @XmlElement(name = "son")
    @XmlIDREF
    private Set<Person> sons = new HashSet<>();

    @XmlElementWrapper(name = "daughters")
    @XmlElement(name = "daughter")
    @XmlIDREF
    private Set<Person> daughters = new HashSet<>();

    @XmlElementWrapper(name = "brothers")
    @XmlElement(name = "brother")
    @XmlIDREF
    private Set<Person> brothers = new HashSet<>();

    @XmlElementWrapper(name = "sisters")
    @XmlElement(name = "sister")
    @XmlIDREF
    private Set<Person> sisters = new HashSet<>();

    @XmlTransient
    private Set<Person> siblings = new HashSet<>();

    @XmlTransient
    private Integer childrenNumber;

    @XmlTransient
    private Integer siblingsNumber;

    @XmlTransient
    private Integer unknownParents = 0;

    public Person() {} // JAXB требует пустой конструктор

    public Person(String id) {
        this.id = id;
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

    public Person getSpouse() { return spouse; }
    public void setSpouse(Person spouse) { this.spouse = spouse; }

    public Person getHusband() { return husband; }
    public void setHusband(Person husband) { this.husband = husband; }

    public Person getWife() { return wife; }
    public void setWife(Person wife) { this.wife = wife; }

    public Person getFather() { return father; }
    public void setFather(Person father) { this.father = father; }

    public Person getMother() { return mother; }
    public void setMother(Person mother) { this.mother = mother; }

    public List<Person> getParents() { return new ArrayList<>(parents); }
    public void addParent(Person parentId) { parents.add(parentId); }

    public List<Person> getChildren() { return new ArrayList<>(children); }
    public void addChild(Person childId) { children.add(childId); }
    public void removeChild(Person childId) { children.remove(childId); }

    public List<Person> getSons() { return new ArrayList<>(sons); }
    public void addSon(Person sonId) { sons.add(sonId); }

    public List<Person> getDaughters() { return new ArrayList<>(daughters); }
    public void addDaughter(Person daughterId) { daughters.add(daughterId); }

    public List<Person> getBrothers() { return new ArrayList<>(brothers); }
    public void addBrother(Person brotherId) { brothers.add(brotherId); }
    public void removeBrother(Person brotherId) { brothers.remove(brotherId); }

    public List<Person> getSisters() { return new ArrayList<>(sisters); }
    public void addSister(Person sisterId) { sisters.add(sisterId); }
    public void removeSister(Person sisterId) { sisters.remove(sisterId); }

    public List<Person> getSiblings() { return new ArrayList<>(siblings); }
    public void addSibling(Person siblingId) { siblings.add(siblingId); }
    public void clearSiblings() { siblings.clear(); }

    public Integer getChildrenNumber() { return childrenNumber; }
    public void setChildrenNumber(Integer childrenNumber) { this.childrenNumber = childrenNumber; }

    public Integer getSiblingsNumber() { return siblingsNumber; }
    public void setSiblingsNumber(Integer siblingsNumber) { this.siblingsNumber = siblingsNumber; }

    public Integer getUnknownParents() { return unknownParents; }
    public void incUnknownParents() { unknownParents++; }

    public void removeDublicatedElements() {
        Set<Person> cleaner = new HashSet<>(parents);
        parents.clear();
        parents.addAll(cleaner);

        cleaner = new HashSet<>(children);
        children.clear();
        children.addAll(cleaner);

        cleaner = new HashSet<>(brothers);
        brothers.clear();
        brothers.addAll(cleaner);

        cleaner = new HashSet<>(sisters);
        sisters.clear();
        sisters.addAll(cleaner);

        cleaner.clear();
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