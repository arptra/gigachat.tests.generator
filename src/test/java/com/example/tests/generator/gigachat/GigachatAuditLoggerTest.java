package com.example.tests.generator.gigachat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GigachatAuditLoggerTest {

    @Test
    void storesEntries() {
        GigachatAuditLogger logger = new GigachatAuditLogger();
        logger.log("request-1", "response-1");
        logger.log("request-2", "response-2");

        List<GigachatExchange> entries = logger.snapshot();
        assertEquals(2, entries.size());
        assertEquals("request-1", entries.get(0).getRequest());
        assertTrue(entries.get(0).getTimestamp().isBefore(entries.get(1).getTimestamp())
                || entries.get(0).getTimestamp().equals(entries.get(1).getTimestamp()));
    }
}
