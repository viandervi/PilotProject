package com.creatorrx.ingestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncJobWorkerTest {
    @Test
    void exponentialBackoffIsBounded() {
        var first = SyncJobWorker.backoff(1);
        var second = SyncJobWorker.backoff(2);
        assertTrue(first.toSeconds() >= 5 && first.toSeconds() <= 6);
        assertTrue(second.toSeconds() >= 10 && second.toSeconds() <= 12);
        assertTrue(SyncJobWorker.backoff(100).toSeconds() <= 4500);
    }
}
