package com.creatorrx.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Server-side creator-specific Google API access. Never accepts arbitrary hosts. */
@Singleton
@Requires(env = "database")
public class CreatorGoogleApiClient {
    private final CreatorTokenProvider tokens;
    private final HttpClient http = HttpClient.create(null);
    private final ObjectMapper mapper = new ObjectMapper();

    public CreatorGoogleApiClient(CreatorTokenProvider tokens) { this.tokens = tokens; }

    public JsonNode get(UUID creatorId, String api, String path, Map<String,String> params) throws Exception {
        String host = switch (api) {
            case "youtube" -> "www.googleapis.com";
            case "analytics" -> "youtubeanalytics.googleapis.com";
            case "reporting" -> "youtubereporting.googleapis.com";
            default -> throw new IllegalArgumentException("Unsupported Google API");
        };
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("?") || path.contains("#"))
            throw new IllegalArgumentException("Invalid Google API path");
        String query = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
        String url = "https://" + host + path + (query.isEmpty() ? "" : "?" + query);
        return mapper.readTree(http.toBlocking().retrieve(HttpRequest.GET(url).bearerAuth(tokens.accessToken(creatorId)), String.class));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
