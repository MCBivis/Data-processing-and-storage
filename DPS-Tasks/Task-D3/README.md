# Task D3: XML to PostgreSQL Import

This program loads XML data from `output.xml` into a PostgreSQL database using a two-phase approach:
1. **Streaming Phase**: Multi-threaded SAX parsing without buffering data in memory
2. **Normalization Phase**: SQL-based transformation to enforce database constraints

## Requirements

- Java 17 or higher
- PostgreSQL database
- Gradle (for building)

## Building

```bash
./gradlew build
```

Or on Windows:
```bash
gradlew.bat build
```

## Database Setup

Before running the program, ensure PostgreSQL is running and create a database:

```sql
CREATE DATABASE task_d3;
```

## Usage

```bash
java -cp build/libs/Task-D3-1.0-SNAPSHOT.jar org.example.Main <db_url> <db_user> <db_password> <xml_file> <num_threads>
```

### Parameters

- `db_url`: PostgreSQL JDBC URL (e.g., `jdbc:postgresql://localhost:5432/task_d3`)
- `db_user`: Database username
- `db_password`: Database password
- `xml_file`: Path to the XML file (e.g., `output.xml`)
- `num_threads`: Number of threads for parallel processing (e.g., `4`)

### Example

```bash
java -cp build/libs/Task-D3-1.0-SNAPSHOT.jar org.example.Main \
  jdbc:postgresql://localhost:5432/task_d3 \
  postgres \
  password \
  output.xml \
  4
```

## Architecture

### Streaming Phase

1. **File Segmentation**: The XML file is split into N segments of approximately equal size
2. **Boundary Adjustment**: Segment boundaries are adjusted to avoid splitting `<person>` elements
3. **Parallel Processing**: Each segment is processed by a separate thread using SAX parser
4. **Temporary Schema**: Data is inserted into temporary tables with minimal constraints:
   - `temp_persons`: id, first_name, last_name, gender
   - `temp_person_relationships`: person_id, related_person_id, relationship_type

### Normalization Phase

1. **Schema Creation**: Creates normalized tables with all constraints:
   - `persons`: id (PK), first_name, last_name, gender (CHECK constraint)
   - `relationships`: person_id, related_person_id, relationship_type (with FOREIGN KEY constraints)
2. **Data Migration**: Copies valid data from temporary tables to normalized tables
3. **Constraint Enforcement**: Foreign keys ensure referential integrity
4. **Index Creation**: Creates indexes for better query performance

## Database Schema

### Temporary Schema (Streaming Phase)

```sql
CREATE TABLE temp_persons (
    id VARCHAR(50) PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    gender VARCHAR(20)
);

CREATE TABLE temp_person_relationships (
    person_id VARCHAR(50),
    related_person_id VARCHAR(50),
    relationship_type VARCHAR(20),
    PRIMARY KEY (person_id, related_person_id, relationship_type)
);
```

### Normalized Schema (After Normalization)

```sql
CREATE TABLE persons (
    id VARCHAR(50) PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('male', 'female'))
);

CREATE TABLE relationships (
    person_id VARCHAR(50) NOT NULL,
    related_person_id VARCHAR(50) NOT NULL,
    relationship_type VARCHAR(20) NOT NULL CHECK (
        relationship_type IN ('wife', 'husband', 'father', 'mother', 
                             'son', 'daughter', 'brother', 'sister')
    ),
    PRIMARY KEY (person_id, related_person_id, relationship_type),
    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    FOREIGN KEY (related_person_id) REFERENCES persons(id) ON DELETE CASCADE
);
```

## Features

- **Streaming Processing**: Uses SAX parser to avoid loading entire XML into memory
- **Multi-threading**: Supports configurable number of threads for parallel processing
- **Smart Segmentation**: Automatically adjusts segment boundaries to avoid splitting XML elements
- **Constraint Enforcement**: Normalization phase enforces all database constraints
- **Performance Optimization**: Creates indexes on temporary tables before normalization

## Notes

- The program uses batch inserts (1000 records per batch) for better performance
- Temporary tables are not automatically dropped after normalization (commented out for debugging)
- The program handles UTF-8 encoded XML files
- Each thread uses its own database connection for parallel processing
