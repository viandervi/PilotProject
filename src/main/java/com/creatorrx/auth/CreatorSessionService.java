package com.creatorrx.auth;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived opaque server sessions; tokens never sent to browsers. */
@Singleton
@Requires(env = "database")
public class CreatorSessionService {
    private record Session(UUID creatorId, Instant expires) {}
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String create(UUID creatorId) {
        sessions.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(sessionId, new Session(creatorId, Instant.now().plusSeconds(8 * 3600)));
        return sessionId;
    }

    public UUID requireCreator(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new SecurityException("Authentication required");
        Session session = sessions.get(sessionId);
        if (session == null || !Instant.now().isBefore(session.expires())) {
            sessions.remove(sessionId);
            throw new SecurityException("Session expired or invalid");
        }
        return session.creatorId();
    }

    public void revoke(String sessionId) { if (sessionId != null) sessions.remove(sessionId); }
}
