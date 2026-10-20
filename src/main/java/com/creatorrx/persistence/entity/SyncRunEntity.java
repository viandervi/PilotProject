package com.creatorrx.persistence.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.naming.NamingStrategies;
import java.time.Instant;
import java.util.UUID;

@MappedEntity(value = "sync_run", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.class)
public record SyncRunEntity(
        @Id UUID id,
        UUID channelId,
        String syncType,
        String status,
        Integer attempts,
        Integer maxAttempts,
        Instant nextAttemptAt,
        Instant leaseUntil,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {}
