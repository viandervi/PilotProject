package com.creatorrx.ingestion;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded polling worker. A job is claimed and finalized using short SQL statements.
 * No processors are registered until a channel/analytics implementation is ready.
 */
@Singleton
@Requires(env = "database")
public class SyncJobWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SyncJobWorker.class);
    private final SyncJobStore store;
    private final List<SyncJobProcessor> processors;

    public SyncJobWorker(SyncJobStore store, List<SyncJobProcessor> processors) {
        this.store = store;
        this.processors = processors;
    }

    @Scheduled(fixedDelay = "${creatorrx.sync.poll-delay:5s}", initialDelay = "10s")
    void poll() {
        try {
            var claim = store.claimNext(Duration.ofMinutes(10));
            if (claim.isEmpty()) return;
            var job = claim.get();
            var processor = processors.stream().filter(p -> p.syncType().equals(job.syncType())).findFirst();
            if (processor.isEmpty()) {
                store.fail(job, false, Duration.ZERO, "NO_PROCESSOR", "No processor registered for " + job.syncType());
                return;
            }
            try {
                if (processor.get() instanceof ChannelSyncProcessor channelSync) channelSync.process(job);
                else processor.get().process(job.channelId());
                if (!store.complete(job)) LOG.warn("Lost sync lease before completion: {}", job.id());
            } catch (Exception failure) {
                // Failures are retryable by default; auth/validation exceptions need classification by future processors.
                var delay = backoff(job.attempt());
                if (!store.fail(job, true, delay, "PROCESSING_ERROR", failure.getClass().getSimpleName())) {
                    LOG.warn("Lost sync lease before failure transition: {}", job.id());
                }
                LOG.warn("Sync processing failed for job {}: {}", job.id(), failure.toString());
            }
        } catch (Exception error) {
            LOG.error("Sync polling failed", error);
        }
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(3600L, 5L * (1L << Math.min(Math.max(0, attempt - 1), 9)));
        return Duration.ofSeconds(seconds + ThreadLocalRandom.current().nextLong(Math.max(1L, seconds / 4)));
    }
}
