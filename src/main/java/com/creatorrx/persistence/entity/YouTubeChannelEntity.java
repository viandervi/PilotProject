package com.creatorrx.persistence.entity;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.naming.NamingStrategies;
import java.time.Instant;
import java.util.UUID;

@MappedEntity(value = "youtube_channel", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.class)
public record YouTubeChannelEntity(@Id UUID id, UUID creatorId, String youtubeChannelId,
                                   String title, String uploadsPlaylistId, Instant createdAt, Instant updatedAt) {}
