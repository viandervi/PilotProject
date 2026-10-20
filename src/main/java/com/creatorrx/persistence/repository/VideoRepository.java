package com.creatorrx.persistence.repository;

import com.creatorrx.persistence.entity.VideoEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.*;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface VideoRepository extends CrudRepository<VideoEntity, UUID> {
    Optional<VideoEntity> findByYoutubeVideoId(String youtubeVideoId);
    List<VideoEntity> findByChannelId(UUID channelId);
}
