package org.example;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public class ElementNames {
    public static Set<String> GetAllNames(String path) throws Exception{

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(path));

        Set<String> elementNames = new HashSet<>();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                elementNames.add(name);
            }
        }

        reader.close();

        return elementNames;
    }
}
