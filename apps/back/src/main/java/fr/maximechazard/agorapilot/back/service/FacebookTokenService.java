package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.publishing.channel.facebook.FacebookProperties;
import fr.maximechazard.agorapilot.back.publishing.channel.facebook.FacebookToken;
import fr.maximechazard.agorapilot.back.repository.FacebookTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookTokenService {

    private final FacebookTokenRepository tokenRepository;
    private final FacebookProperties props;
    private final RestTemplate restTemplate;

    @Value("${facebook.api-base-url}")
    private String GRAPH_URL;

    public String getCurrentPageToken() {
        return tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc("page")
                .map(FacebookToken::getAccessToken)
                .orElseThrow(() -> new IllegalStateException("No token available"));
    }

    public void bootstrap(String shortLivedUserToken) {
        String longLivedToken = exchangeForLongLivedToken(shortLivedUserToken);
        String pageToken = fetchPageToken(longLivedToken);
        TokenInfo info = inspectToken(pageToken);
        saveToken(pageToken, info.expiresAt());
    }

    public void renewIfNeeded() {
        FacebookToken token = tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc("page")
                .orElse(null);

        if (token == null) {
            log.warn("No token available, launch bootstrap.");
            return;
        }

        long daysLeft = LocalDateTime.now().until(token.getExpiresAt(), ChronoUnit.DAYS);

        if (daysLeft <= 15) { // days threshold
            String refreshed = exchangeForLongLivedToken(token.getAccessToken());
            TokenInfo info = inspectToken(refreshed);
            saveToken(refreshed, info.expiresAt());
        }
    }

    private String exchangeForLongLivedToken(String shortToken) {
        String url = UriComponentsBuilder.fromUri(URI.create(GRAPH_URL + "/oauth/access_token"))
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", props.clientId())
                .queryParam("client_secret", props.clientSecret())
                .queryParam("fb_exchange_token", shortToken)
                .toUriString();

        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        return (String) Objects.requireNonNull(response).get("access_token");
    }

    private String fetchPageToken(String userLongToken) {
        String url = UriComponentsBuilder.fromUri(URI.create(GRAPH_URL + "/" + props.pageId()))
                .queryParam("fields", "access_token")
                .queryParam("access_token", userLongToken)
                .toUriString();

        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        return (String) Objects.requireNonNull(response).get("access_token");
    }

    private TokenInfo inspectToken(String token) {
        String url = UriComponentsBuilder.fromUri(URI.create(GRAPH_URL + "/debug_token"))
                .queryParam("input_token", token)
                .queryParam("access_token", props.clientId() + "|" + props.clientSecret())
                .toUriString();

        Map<?, ?> root = restTemplate.getForObject(url, Map.class);
        Map<?, ?> data = (Map<?, ?>) Objects.requireNonNull(root).get("data");

        boolean isValid = Boolean.TRUE.equals(data.get("is_valid"));
        if (!isValid) throw new IllegalStateException("Invalid token");

        Object expiresAtRaw = data.get("expires_at");
        long expiresAtEpoch = expiresAtRaw != null ? ((Number) expiresAtRaw).longValue() : 0L;
        LocalDateTime expiresAt = expiresAtEpoch > 0
                ? LocalDateTime.ofEpochSecond(expiresAtEpoch, 0, ZoneOffset.UTC)
                : LocalDateTime.now().plusYears(10); // permanent token

        return new TokenInfo(expiresAt);
    }

    private void saveToken(String accessToken, LocalDateTime expiresAt) {
        FacebookToken entity = tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc("page")
                .orElse(new FacebookToken());
        entity.setAccessToken(accessToken);
        entity.setTokenType("page");
        entity.setExpiresAt(expiresAt);
        tokenRepository.save(entity);
    }

    record TokenInfo(LocalDateTime expiresAt) {
    }
}