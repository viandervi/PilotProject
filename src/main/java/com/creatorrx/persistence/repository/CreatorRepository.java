package com.creatorrx.persistence.repository;

import com.creatorrx.persistence.entity.CreatorEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.*;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CreatorRepository extends CrudRepository<CreatorEntity, UUID> {
    Optional<CreatorEntity> findByGoogleSubject(String googleSubject);
}
