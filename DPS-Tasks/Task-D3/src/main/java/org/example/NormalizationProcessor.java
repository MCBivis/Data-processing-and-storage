package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the normalization phase: transforms temporary schema data into normalized schema
 * with all constraints enforced.
 */
public class NormalizationProcessor {
    
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    
    public NormalizationProcessor(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }
    
    /**
     * Executes the normalization phase:
     * 1. Creates normalized schema
     * 2. Copies valid data from temporary tables
     * 3. Enforces all constraints
     */
    public void execute() throws SQLException {
        System.out.println("Starting normalization phase...");
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);
            
            // Create normalized schema
            DatabaseSchema.createNormalizedSchema(conn);
            conn.commit();
            
            // Step 1: Insert persons (only those with valid data)
            System.out.println("Step 1: Copying persons to normalized schema...");
            try (Statement stmt = conn.createStatement()) {
                int personCount = stmt.executeUpdate("""
                    INSERT INTO persons (id, first_name, last_name, gender)
                    SELECT id, first_name, last_name, gender
                    FROM temp_persons
                    WHERE id IS NOT NULL 
                      AND first_name IS NOT NULL 
                      AND last_name IS NOT NULL 
                      AND gender IN ('male', 'female')
                """);
                System.out.println("Inserted " + personCount + " persons");
                conn.commit();
            }
            
            // Step 2: Insert relationships (only for persons that exist in both tables)
            System.out.println("Step 2: Copying relationships to normalized schema...");
            try (Statement stmt = conn.createStatement()) {
                int relationshipCount = stmt.executeUpdate("""
                    INSERT INTO relationships (person_id, related_person_id, relationship_type)
                    SELECT tpr.person_id,
                           tpr.related_person_id,
                           tpr.relationship_type
                    FROM temp_person_relationships tpr
                    WHERE tpr.relationship_type IN ('parent', 'child')
                      AND EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.person_id)
                      AND EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.related_person_id)
                    
                    UNION ALL
                    
                    SELECT LEAST(tpr.person_id, tpr.related_person_id)  AS person_id,
                           GREATEST(tpr.person_id, tpr.related_person_id) AS related_person_id,
                           tpr.relationship_type
                    FROM temp_person_relationships tpr
                    WHERE tpr.relationship_type IN ('spouse', 'sibling')
                      AND EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.person_id)
                      AND EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.related_person_id)
                    GROUP BY LEAST(tpr.person_id, tpr.related_person_id),
                             GREATEST(tpr.person_id, tpr.related_person_id),
                             tpr.relationship_type
                """);
                System.out.println("Inserted " + relationshipCount + " relationships");
                conn.commit();
            }
            
            // Step 3: Create indexes on normalized tables for better query performance
            System.out.println("Step 3: Creating indexes on normalized tables...");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_persons_gender ON persons(gender)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_person_id ON relationships(person_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_related_id ON relationships(related_person_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_type ON relationships(relationship_type)");
                conn.commit();
            }
            
            // Step 4: Verify data integrity
            System.out.println("Step 4: Verifying data integrity...");
            try (Statement stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM persons");
                if (rs.next()) {
                    System.out.println("Total persons in normalized schema: " + rs.getInt(1));
                }
                
                rs = stmt.executeQuery("SELECT COUNT(*) FROM relationships");
                if (rs.next()) {
                    System.out.println("Total relationships in normalized schema: " + rs.getInt(1));
                }
                
                // Check for orphaned relationships (should be 0 due to foreign keys)
                rs = stmt.executeQuery("""
                    SELECT COUNT(*) FROM temp_person_relationships tpr
                    WHERE NOT EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.person_id)
                       OR NOT EXISTS (SELECT 1 FROM persons p WHERE p.id = tpr.related_person_id)
                """);
                if (rs.next()) {
                    int orphaned = rs.getInt(1);
                    if (orphaned > 0) {
                        System.out.println("Warning: " + orphaned + " orphaned relationships were not imported");
                    }
                }
            }
            
            // Step 5: Optionally drop temporary tables (commented out for debugging)
             System.out.println("Step 5: Cleaning up temporary tables...");
             try (Statement stmt = conn.createStatement()) {
                 stmt.execute("DROP TABLE IF EXISTS temp_person_relationships CASCADE");
                 stmt.execute("DROP TABLE IF EXISTS temp_persons CASCADE");
                 conn.commit();
             }
            
            conn.commit();
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("Normalization phase completed in " + (endTime - startTime) + " ms");
    }
}
