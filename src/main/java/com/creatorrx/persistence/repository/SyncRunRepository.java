package com.creatorrx.persistence.repository;

import com.creatorrx.persistence.entity.SyncRunEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface SyncRunRepository extends CrudRepository<SyncRunEntity, UUID> {
    List<SyncRunEntity> findByChannelId(UUID channelId);
}
