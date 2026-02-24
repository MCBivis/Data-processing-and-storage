package org.example;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database schema creation for both temporary (streaming) and normalized phases.
 */
public class DatabaseSchema {
    
    /**
     * Creates a temporary schema with minimal constraints for the streaming phase.
     * This allows fast insertion without foreign key lookups.
     */
    public static void createTemporarySchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing tables if they exist
            stmt.execute("DROP TABLE IF EXISTS temp_person_relationships CASCADE");
            stmt.execute("DROP TABLE IF EXISTS temp_persons CASCADE");
            
            // Create temporary persons table with minimal constraints
            stmt.execute("""
                CREATE TABLE temp_persons (
                    id VARCHAR(50) PRIMARY KEY,
                    first_name VARCHAR(255),
                    last_name VARCHAR(255),
                    gender VARCHAR(20)
                )
            """);
            
            // Create temporary relationships table
            stmt.execute("""
                CREATE TABLE temp_person_relationships (
                    person_id VARCHAR(50),
                    related_person_id VARCHAR(50),
                    relationship_type VARCHAR(20),
                    PRIMARY KEY (person_id, related_person_id, relationship_type)
                )
            """);
            
            System.out.println("Temporary schema created successfully");
        }
    }
    
    /**
     * Creates the normalized schema with all constraints as defined in task D2.
     * This is the target schema after normalization.
     */
    public static void createNormalizedSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing tables if they exist
            stmt.execute("DROP TABLE IF EXISTS relationships CASCADE");
            stmt.execute("DROP TABLE IF EXISTS persons CASCADE");
            
            // Create normalized persons table
            stmt.execute("""
                CREATE TABLE persons (
                    id VARCHAR(50) PRIMARY KEY,
                    first_name VARCHAR(255) NOT NULL,
                    last_name VARCHAR(255) NOT NULL,
                    gender VARCHAR(20) NOT NULL CHECK (gender IN ('male', 'female'))
                )
            """);
            
            // Create normalized relationships table with foreign key constraints
            stmt.execute("""
                CREATE TABLE relationships (
                    person_id VARCHAR(50) NOT NULL,
                    related_person_id VARCHAR(50) NOT NULL,
                    relationship_type VARCHAR(20) NOT NULL CHECK (
                        relationship_type IN ('spouse', 'parent', 'child', 'sibling')
                    ),
                    PRIMARY KEY (person_id, related_person_id, relationship_type),
                    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
                    FOREIGN KEY (related_person_id) REFERENCES persons(id) ON DELETE CASCADE
                )
            """);
            
            System.out.println("Normalized schema created successfully");
        }
    }
    
    /**
     * Creates indexes on temporary tables to speed up normalization phase lookups.
     */
    public static void createTemporaryIndexes(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_persons_id ON temp_persons(id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_relationships_person_id ON temp_person_relationships(person_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_relationships_related_id ON temp_person_relationships(related_person_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_relationships_type ON temp_person_relationships(relationship_type)");
            
            System.out.println("Temporary indexes created successfully");
        }
    }
}
