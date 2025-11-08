package org.example;

import jakarta.xml.bind.annotation.*;
import java.util.*;

@XmlRootElement(name = "people")
@XmlAccessorType(XmlAccessType.FIELD)
public class People {
    @XmlAttribute
    private int count;

    @XmlElement(name = "person")
    private List<PersonForOutput> people = new ArrayList<>();

    public People() {}

    public People(Collection<Person> persons) {
        this.people = new ArrayList<>();
        for (Person person : persons) {
            this.people.add(new PersonForOutput(person));
        }
        this.count = people.size();
    }
}

