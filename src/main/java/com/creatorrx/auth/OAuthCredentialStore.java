package com.creatorrx.auth;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Creator-scoped refresh-token storage; plaintext never reaches PostgreSQL. */
@Singleton
@Requires(env = "database")
public class OAuthCredentialStore {
    private final DataSource datasource;
    private final RefreshTokenCipher cipher;

    public OAuthCredentialStore(DataSource datasource, RefreshTokenCipher cipher) {
        this.datasource = datasource;
        this.cipher = cipher;
    }

    public void save(UUID creatorId, String refreshToken) throws Exception {
        String encrypted = cipher.encrypt(refreshToken, creatorId.toString());
        String sql = """
            INSERT INTO oauth_credential(id, creator_id, encrypted_refresh_token, key_version)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (creator_id) DO UPDATE
            SET encrypted_refresh_token = EXCLUDED.encrypted_refresh_token,
                key_version = EXCLUDED.key_version, updated_at = now()
            """;
        try (var connection = datasource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, creatorId);
            stmt.setString(3, encrypted);
            stmt.executeUpdate();
        }
    }

    public Optional<String> find(UUID creatorId) throws Exception {
        try (var connection = datasource.getConnection();
             var stmt = connection.prepareStatement("SELECT encrypted_refresh_token FROM oauth_credential WHERE creator_id = ?")) {
            stmt.setObject(1, creatorId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(cipher.decrypt(rs.getString(1), creatorId.toString()));
            }
        }
    }

    public boolean delete(UUID creatorId) throws SQLException {
        try (var connection = datasource.getConnection();
             var stmt = connection.prepareStatement("DELETE FROM oauth_credential WHERE creator_id = ?")) {
            stmt.setObject(1, creatorId);
            return stmt.executeUpdate() > 0;
        }
    }
}
