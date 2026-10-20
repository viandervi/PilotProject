package com.creatorrx.persistence.entity;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.naming.NamingStrategies;
import java.time.Instant;
import java.util.UUID;

@MappedEntity(value = "creator", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.class)
public record CreatorEntity(@Id UUID id, String googleSubject, Instant createdAt, Instant updatedAt) {}
