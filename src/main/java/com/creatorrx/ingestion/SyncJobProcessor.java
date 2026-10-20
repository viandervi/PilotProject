package com.creatorrx.ingestion;

import java.util.UUID;

/** Implementations must be idempotent: a lease can expire and work can be retried. */
public interface SyncJobProcessor {
    String syncType();
    void process(UUID channelId) throws Exception;
}
