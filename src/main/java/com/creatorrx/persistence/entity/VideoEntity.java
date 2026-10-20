package com.creatorrx.persistence.entity;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.naming.NamingStrategies;
import java.time.Instant;
import java.util.UUID;

@MappedEntity(value = "video", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.class)
public record VideoEntity(@Id UUID id, UUID channelId, String youtubeVideoId, String title,
                          Instant publishedAt, Integer durationSeconds, Instant createdAt, Instant updatedAt) {}
