package com.creatorrx.persistence.repository;

import com.creatorrx.persistence.entity.YouTubeChannelEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.*;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface YouTubeChannelRepository extends CrudRepository<YouTubeChannelEntity, UUID> {
    Optional<YouTubeChannelEntity> findByYoutubeChannelId(String youtubeChannelId);
    List<YouTubeChannelEntity> findByCreatorId(UUID creatorId);
}
