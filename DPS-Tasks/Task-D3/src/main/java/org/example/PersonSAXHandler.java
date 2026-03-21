package org.example;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SAX handler for parsing person elements and inserting them into the database.
 * This handler processes XML in a streaming fashion without buffering the entire document.
 */
public class PersonSAXHandler extends DefaultHandler {
    
    private final Connection connection;
    private PreparedStatement personStmt;
    private PreparedStatement relationshipStmt;
    
    private String currentPersonId;
    private String currentFirstName;
    private String currentLastName;
    private String currentGender;
    private String currentElement;
    private StringBuilder currentText;
    
    private final List<Relationship> pendingRelationships = new ArrayList<>();
    private int personCount = 0;
    private static final int BATCH_SIZE = 1000;
    
    public PersonSAXHandler(Connection connection) throws SQLException {
        this.connection = connection;
        this.personStmt = connection.prepareStatement(
            "INSERT INTO temp_persons (id, first_name, last_name, gender) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (id) DO NOTHING"
        );
        this.relationshipStmt = connection.prepareStatement(
            "INSERT INTO temp_person_relationships (person_id, related_person_id, relationship_type) " +
            "VALUES (?, ?, ?) ON CONFLICT DO NOTHING"
        );
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentElement = qName;
        currentText = new StringBuilder();
        
        if ("person".equals(qName)) {
            currentPersonId = attributes.getValue("id");
            currentFirstName = null;
            currentLastName = null;
            currentGender = null;
            pendingRelationships.clear();
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentText != null) {
            currentText.append(ch, start, length);
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        // Text content is only relevant for leaf elements; for container elements
        // like <person>, we don't rely on the text value at all.
        String text = currentText != null ? currentText.toString().trim() : "";

        switch (qName) {
            case "firstName":
                currentFirstName = text;
                break;
            case "lastName":
                currentLastName = text;
                break;
            case "gender":
                currentGender = text;
                break;
            case "wife":
            case "husband":
                if (!text.isEmpty()) {
                    pendingRelationships.add(new Relationship(currentPersonId, text, "spouse"));
                }
                break;
            case "father":
            case "mother":
                if (!text.isEmpty()) {
                    pendingRelationships.add(new Relationship(currentPersonId, text, "parent"));
                }
                break;
            case "son":
            case "daughter":
                if (!text.isEmpty()) {
                    pendingRelationships.add(new Relationship(currentPersonId, text, "child"));
                }
                break;
            case "brother":
            case "sister":
                if (!text.isEmpty()) {
                    pendingRelationships.add(new Relationship(currentPersonId, text, "sibling"));
                }
                break;
            case "person":
                // Insert person and relationships
                try {
                    insertPerson();
                    insertRelationships();
                    
                    personCount++;
                    if (personCount % BATCH_SIZE == 0) {
                        personStmt.executeBatch();
                        relationshipStmt.executeBatch();
                        connection.commit();
                    }
                } catch (SQLException e) {
                    throw new SAXException("Database error", e);
                }
                break;
        }
        
        currentText = null;
    }
    
    private void insertPerson() throws SQLException {
        if (currentPersonId != null && currentFirstName != null && 
            currentLastName != null && currentGender != null) {
            personStmt.setString(1, currentPersonId);
            personStmt.setString(2, currentFirstName);
            personStmt.setString(3, currentLastName);
            personStmt.setString(4, currentGender);
            personStmt.addBatch();
        }
    }
    
    private void insertRelationships() throws SQLException {
        for (Relationship rel : pendingRelationships) {
            relationshipStmt.setString(1, rel.personId);
            relationshipStmt.setString(2, rel.relatedPersonId);
            relationshipStmt.setString(3, rel.relationshipType);
            relationshipStmt.addBatch();
        }
    }
    
    public void finish() throws SQLException {
        personStmt.executeBatch();
        relationshipStmt.executeBatch();
        connection.commit();
    }
    
    public void close() throws SQLException {
        if (personStmt != null) {
            personStmt.close();
        }
        if (relationshipStmt != null) {
            relationshipStmt.close();
        }
    }
    
    public int getPersonCount() {
        return personCount;
    }
    
    private static class Relationship {
        final String personId;
        final String relatedPersonId;
        final String relationshipType;
        
        Relationship(String personId, String relatedPersonId, String relationshipType) {
            this.personId = personId;
            this.relatedPersonId = relatedPersonId;
            this.relationshipType = relationshipType;
        }
    }
}
