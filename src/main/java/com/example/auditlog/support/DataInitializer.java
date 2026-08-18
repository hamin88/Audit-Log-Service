package com.example.auditlog.support;

import com.example.auditlog.domain.LedgerHead;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Initializes the singleton ledger head entity at application startup.
 * This ensures that concurrent append operations don't trigger race conditions
 * when trying to create the singleton for the first time.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final EntityManager entityManager;

    public DataInitializer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
        initializeLedgerHead();
    }

    @Transactional
    public void initializeLedgerHead() {
        // Check if ledger head already exists
        LedgerHead head = entityManager.find(LedgerHead.class, "HEAD", LockModeType.PESSIMISTIC_WRITE);
        if (head == null) {
            // Create and persist the singleton ledger head
            head = new LedgerHead(null);
            try {
                entityManager.persist(head);
                entityManager.flush();
            } catch (Exception e) {
                // Singleton may have been created by another instance; ignore
            }
        }
    }
}
