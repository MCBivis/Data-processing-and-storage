package org.example;

import jakarta.xml.bind.annotation.*;
import java.util.*;

@XmlRootElement(name = "person")
@XmlAccessorType(XmlAccessType.FIELD)
public class PersonForOutput {
    @XmlID
    @XmlAttribute(name = "id")
    private String id;

    @XmlElement
    private String firstName;

    @XmlElement
    private String lastName;

    @XmlElement
    private String gender;

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

    public PersonForOutput() {} // JAXB требует пустой конструктор

    public PersonForOutput(Person p) {
        this.id = p.getId();
        this.firstName = p.getFirstName();
        this.lastName = p.getLastName();
        this.gender = p.getGender();
        this.husband = p.getHusband();
        this.wife = p.getWife();
        this.father = p.getFather();
        this.mother = p.getMother();
        if (!p.getSons().isEmpty()) {
            this.sons.addAll(p.getSons());
        } else {
            this.sons = null;
        }
        if (!p.getDaughters().isEmpty()) {
            this.daughters.addAll(p.getDaughters());
        } else {
            this.daughters = null;
        }
        if (!p.getBrothers().isEmpty()) {
            this.brothers.addAll(p.getBrothers());
        } else {
            this.brothers = null;
        }
        if (!p.getSisters().isEmpty()) {
            this.sisters.addAll(p.getSisters());
        } else {
            this.sisters = null;
        }
    }
}