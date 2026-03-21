package org.example;

import java.io.File;

/**
 * Main entry point for Task D3: Import XML data into PostgreSQL database.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <num_threads>");
            System.exit(1);
        }
        
        String dbUrl = "jdbc:postgresql://localhost:5432/dpsdb";
        String dbUser = "postgres";
        String dbPassword = "123";
        String xmlFilePath = "output.xml";
        int numThreads;
        
        try {
            numThreads = Integer.parseInt(args[0]);
            if (numThreads < 1) {
                throw new NumberFormatException("Number of threads must be at least 1");
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: num_threads must be a positive integer");
            System.exit(1);
            return;
        }
        
        File xmlFile = new File(xmlFilePath);
        if (!xmlFile.exists() || !xmlFile.isFile()) {
            System.err.println("Error: XML file not found: " + xmlFilePath);
            System.exit(1);
            return;
        }
        
        System.out.println("=== Task D3: XML to PostgreSQL Import ===");
        System.out.println("Database URL: " + dbUrl);
        System.out.println("Database User: " + dbUser);
        System.out.println("XML File: " + xmlFilePath);
        System.out.println("Number of Threads: " + numThreads);
        System.out.println();
        
        try {
            // Phase 1: Streaming phase
            System.out.println("=== PHASE 1: STREAMING ===");
            StreamingProcessor streamingProcessor = new StreamingProcessor(
                dbUrl, dbUser, dbPassword, xmlFile, numThreads
            );
            streamingProcessor.execute();
            
            System.out.println();
            
            // Phase 2: Normalization phase
            System.out.println("=== PHASE 2: NORMALIZATION ===");
            NormalizationProcessor normalizationProcessor = new NormalizationProcessor(
                dbUrl, dbUser, dbPassword
            );
            normalizationProcessor.execute();
            
            System.out.println();
            System.out.println("=== Import completed successfully! ===");
            
        } catch (Exception e) {
            System.err.println("Error during import: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
