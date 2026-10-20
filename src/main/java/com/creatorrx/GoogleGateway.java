package com.creatorrx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.*;

@Singleton
public class GoogleGateway {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = createClient();
    private static HttpClient createClient() {
        var config = new io.micronaut.http.client.DefaultHttpClientConfiguration();
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy);
            config.setProxyType(java.net.Proxy.Type.HTTP);
            config.setProxyAddress(new java.net.InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()));
        }
        return HttpClient.create(null, config);
    }
    private final Map<String, Pending> pending = new HashMap<>();
    private String accessToken, refreshToken;
    private Instant expires = Instant.EPOCH;
    private record Pending(Instant created, String verifier, String nonce) {}
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Configure " + name + " before connecting Google.");
        return value;
    }
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static String query(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
    }
    private static String random() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String redirect() { return System.getenv().getOrDefault("GOOGLE_REDIRECT_URI", "http://localhost:8080/oauth/callback"); }
    public synchronized String authorize() throws Exception {
        String clientId = env("GOOGLE_CLIENT_ID"); env("GOOGLE_CLIENT_SECRET");
        pending.entrySet().removeIf(e -> e.getValue().created().isBefore(Instant.now().minusSeconds(600)));
        if (pending.size() >= 100) throw new IllegalStateException("Too many pending logins; retry later.");
        String state = random(), verifier = random(), nonce = random();
        pending.put(state, new Pending(Instant.now(), verifier, nonce));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        return "https://accounts.google.com/o/oauth2/v2/auth?" + query(Map.of(
            "client_id", clientId, "redirect_uri", redirect(), "response_type", "code", "state", state,
            "scope", "openid https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/yt-analytics.readonly",
            "access_type", "offline", "prompt", "select_account consent", "nonce", nonce, "code_challenge", challenge, "code_challenge_method", "S256"));
    }
    public synchronized void callback(String state, String code) throws Exception {
        Pending login = pending.remove(state);
        if (login == null || login.created().isBefore(Instant.now().minusSeconds(600))) throw new IllegalArgumentException("Invalid or expired OAuth state. Start login again.");
        store(form(Map.of("code", code, "client_id", env("GOOGLE_CLIENT_ID"), "client_secret", env("GOOGLE_CLIENT_SECRET"), "redirect_uri", redirect(), "grant_type", "authorization_code", "code_verifier", login.verifier())), true);
    }
    /** Database-mode OAuth exchange: no global token state is modified. */
    public synchronized OAuthExchange exchangeForCreator(String state, String code) throws Exception {
        Pending login = pending.remove(state);
        if (login == null || login.created().isBefore(Instant.now().minusSeconds(600)))
            throw new IllegalArgumentException("Invalid or expired OAuth state. Start login again.");
        JsonNode response = form(Map.of("code", code, "client_id", env("GOOGLE_CLIENT_ID"),
            "client_secret", env("GOOGLE_CLIENT_SECRET"), "redirect_uri", redirect(),
            "grant_type", "authorization_code", "code_verifier", login.verifier()));
        String refresh = response.path("refresh_token").asText("");
        String idToken = response.path("id_token").asText("");
        if (refresh.isBlank() || idToken.isBlank()) throw new IllegalStateException("Google login missing refresh or ID token; reconnect.");
        return new OAuthExchange(idToken, refresh, login.nonce());
    }
    public record OAuthExchange(String idToken, String refreshToken, String nonce) {}
    private JsonNode form(Map<String,String> body) throws Exception {
        return send(HttpRequest.POST("https://oauth2.googleapis.com/token", query(body)).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }
    private void store(JsonNode token, boolean newLogin) {
        if (!token.hasNonNull("access_token")) throw new IllegalStateException("Google returned no access token.");
        accessToken = token.path("access_token").asText();
        if (newLogin) refreshToken = null;
        if (token.hasNonNull("refresh_token")) refreshToken = token.path("refresh_token").asText();
        expires = Instant.now().plusSeconds(token.path("expires_in").asLong(3600) - 60);
    }
    private synchronized String token() throws Exception {
        if (accessToken == null) throw new IllegalStateException("Connect Google using /auth/google first.");
        if (Instant.now().isAfter(expires)) {
            if (refreshToken == null) throw new IllegalStateException("Session expired; reconnect Google.");
            store(form(Map.of("client_id", env("GOOGLE_CLIENT_ID"), "client_secret", env("GOOGLE_CLIENT_SECRET"), "refresh_token", refreshToken, "grant_type", "refresh_token")), false);
        }
        return accessToken;
    }
    private JsonNode send(MutableHttpRequest<?> request) throws Exception {
        try { return json.readTree(client.toBlocking().retrieve(request, String.class)); }
        catch (HttpClientResponseException e) { throw new GoogleFailure(e.getStatus().getCode()); }
    }
    public JsonNode get(String path, Map<String,String> params) throws Exception {
        return send(HttpRequest.GET("https://" + path + "?" + query(params)).bearerAuth(token()));
    }
    public JsonNode post(String path, Map<String,String> body) throws Exception {
        return send(HttpRequest.POST("https://" + path, json.writeValueAsString(body)).contentType(MediaType.APPLICATION_JSON_TYPE).bearerAuth(token()));
    }
    static URI validateDownload(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"youtubereporting.googleapis.com".equals(uri.getHost()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            throw new IllegalArgumentException("Unexpected Reporting API download destination.");
        return uri;
    }
    public String download(String url) throws Exception {
        URI uri = validateDownload(url);
        try { return client.toBlocking().retrieve(HttpRequest.GET(uri).bearerAuth(token()), String.class); }
        catch (HttpClientResponseException e) { throw new GoogleFailure(e.getStatus().getCode()); }
    }
    static class GoogleFailure extends RuntimeException {
        final int status;
        GoogleFailure(int status) { super("Google API returned HTTP " + status + ". Check API enablement, permissions, quota and report availability."); this.status = status; }
    }
}
