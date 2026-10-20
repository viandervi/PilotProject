package com.creatorrx.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.List;

/** Google's verifier validates signature, issuer, audience and expiry. */
@Singleton
@Requires(env = "database")
public class GoogleIdentityVerifier {
    private final GoogleIdTokenVerifier verifier;
    public GoogleIdentityVerifier() {
        String clientId = System.getenv("GOOGLE_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) throw new IllegalStateException("GOOGLE_CLIENT_ID is required");
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(List.of(clientId)).build();
    }
    public String verifySubject(String idToken, String expectedNonce) throws Exception {
        if (idToken == null || idToken.isBlank() || expectedNonce == null || expectedNonce.isBlank())
            throw new IllegalArgumentException("Missing identity token or nonce");
        GoogleIdToken token = verifier.verify(idToken);
        if (token == null || !expectedNonce.equals(token.getPayload().get("nonce")))
            throw new IllegalArgumentException("Invalid Google identity token or nonce");
        String subject = token.getPayload().getSubject();
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Missing Google subject");
        return subject;
    }
}
