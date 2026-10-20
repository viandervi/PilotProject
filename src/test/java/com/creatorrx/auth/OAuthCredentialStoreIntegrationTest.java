package com.creatorrx.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.postgresql.ds.PGSimpleDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OAuthCredentialStoreIntegrationTest {
    @Test
    void encryptsTokensAndSeparatesCreators() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            var ds = new PGSimpleDataSource();
            ds.setURL(postgres.getJdbcUrl());
            ds.setUser(postgres.getUsername());
            ds.setPassword(postgres.getPassword());
            // Key is injected into process environment in CI for this integration test.
            var key = System.getenv("CREATORRX_TOKEN_ENCRYPTION_KEY");
            if (key == null || key.isBlank()) return;
            var cipher = new RefreshTokenCipher();
            var identities = new CreatorIdentityStore(ds);
            UUID a = identities.findOrCreate("google-a");
            UUID b = identities.findOrCreate("google-b");
            assertEquals(a, identities.findOrCreate("google-a"));
            var store = new OAuthCredentialStore(ds, cipher);
            store.save(a, "refresh-a");
            store.save(b, "refresh-b");
            assertEquals("refresh-a", store.find(a).orElseThrow());
            assertEquals("refresh-b", store.find(b).orElseThrow());
            store.save(a, "refresh-a-new");
            assertEquals("refresh-a-new", store.find(a).orElseThrow());
            assertEquals("refresh-b", store.find(b).orElseThrow());
            assertTrue(store.delete(a));
            assertTrue(store.find(a).isEmpty());
            assertThrows(Exception.class, () -> cipher.decrypt(cipher.encrypt("secret", a.toString()), b.toString()));
        }
    }
}
