package com.creatorrx.auth;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.util.UUID;

/** Persist Google OIDC subject as the stable creator identity. */
@Singleton
@Requires(env = "database")
public class CreatorIdentityStore {
    private final DataSource dataSource;
    public CreatorIdentityStore(DataSource dataSource) { this.dataSource = dataSource; }
    public UUID findOrCreate(String subject) throws Exception {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Missing Google subject");
        String sql = """
            INSERT INTO creator(id, google_subject) VALUES (?, ?)
            ON CONFLICT (google_subject) DO UPDATE SET updated_at = creator.updated_at
            RETURNING id
            """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, subject);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Unable to create creator");
                return rows.getObject(1, UUID.class);
            }
        }
    }
}
