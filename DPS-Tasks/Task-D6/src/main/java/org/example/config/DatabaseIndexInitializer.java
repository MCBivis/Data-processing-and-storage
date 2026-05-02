package org.example.config;

import org.example.repository.DatabaseIndexRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates supporting indexes for reference, schedule, route search, and booking flows.
 */
@Component
@Order(0)
public class DatabaseIndexInitializer implements ApplicationRunner {

    private final DatabaseIndexRepository databaseIndexRepository;

    public DatabaseIndexInitializer(DatabaseIndexRepository databaseIndexRepository) {
        this.databaseIndexRepository = databaseIndexRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        databaseIndexRepository.ensureReferenceAndSearchIndexes();
    }
}
