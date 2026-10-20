package com.creatorrx.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Refreshes access tokens per creator, never using a process-global Google account. */
@Singleton
@Requires(env = "database")
public class CreatorTokenProvider {
    private record Cached(String token, long expiresAtMillis) {}
    private final OAuthCredentialStore credentials;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.create(null);

    public CreatorTokenProvider(OAuthCredentialStore credentials) { this.credentials = credentials; }

    public String accessToken(UUID creatorId) throws Exception {
        Cached current = cache.get(creatorId);
        if (current != null && current.expiresAtMillis() > System.currentTimeMillis()) return current.token();
        synchronized (locks.computeIfAbsent(creatorId, ignored -> new Object())) {
            current = cache.get(creatorId);
            if (current != null && current.expiresAtMillis() > System.currentTimeMillis()) return current.token();
            String refresh = credentials.find(creatorId).orElseThrow(() -> new IllegalStateException("Creator must reconnect Google"));
            String clientId = required("GOOGLE_CLIENT_ID");
            String clientSecret = required("GOOGLE_CLIENT_SECRET");
            String body = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                    + "&refresh_token=" + encode(refresh) + "&grant_type=refresh_token";
            JsonNode response = mapper.readTree(http.toBlocking().retrieve(
                    HttpRequest.POST("https://oauth2.googleapis.com/token", body)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class));
            String access = response.path("access_token").asText("");
            if (access.isBlank()) throw new IllegalStateException("Google did not return an access token");
            if (response.hasNonNull("refresh_token")) credentials.save(creatorId, response.get("refresh_token").asText());
            long expiry = System.currentTimeMillis() + Math.max(30, response.path("expires_in").asLong(3600) - 60) * 1000L;
            cache.put(creatorId, new Cached(access, expiry));
            return access;
        }
    }

    public void disconnect(UUID creatorId) throws Exception {
        synchronized (locks.computeIfAbsent(creatorId, ignored -> new Object())) {
            credentials.delete(creatorId);
            cache.remove(creatorId);
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required");
        return value;
    }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
