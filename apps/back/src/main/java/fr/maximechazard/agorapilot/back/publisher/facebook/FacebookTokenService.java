package fr.maximechazard.agorapilot.back.publisher.facebook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookTokenService {

    static final String PAGE_TOKEN_TYPE = "page";
    static final String USER_TOKEN_TYPE = "user";
    private static final long RENEWAL_THRESHOLD_DAYS = 15;

    private final FacebookTokenRepository tokenRepository;
    private final FacebookProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public String getCurrentPageToken() {
        return tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc(PAGE_TOKEN_TYPE)
                .map(FacebookToken::getAccessToken)
                .orElseThrow(() -> new IllegalStateException("No token available"));
    }

    public Optional<TokenStatus> getTokenStatus(String tokenType) {
        return tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(tokenType)
                .map(token -> new TokenStatus(
                        preview(token.getAccessToken()),
                        token.getExpiresAt(),
                        LocalDateTime.now().until(token.getExpiresAt(), ChronoUnit.DAYS)
                ));
    }

    public void bootstrap(String shortLivedUserToken) {
        String longLivedUserToken = exchangeForLongLivedToken(shortLivedUserToken);
        saveToken(USER_TOKEN_TYPE, longLivedUserToken, inspectToken(longLivedUserToken).expiresAt());

        String pageToken = fetchPageToken(longLivedUserToken);
        saveToken(PAGE_TOKEN_TYPE, pageToken, inspectToken(pageToken).expiresAt());
    }

    public void renewIfNeeded() {
        FacebookToken pageToken = tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc(PAGE_TOKEN_TYPE)
                .orElse(null);

        if (pageToken == null) {
            log.warn("No page token available, bootstrap required.");
            return;
        }

        long daysLeft = LocalDateTime.now().until(pageToken.getExpiresAt(), ChronoUnit.DAYS);
        if (daysLeft > RENEWAL_THRESHOLD_DAYS) {
            return;
        }

        FacebookToken userToken = tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc(USER_TOKEN_TYPE)
                .orElse(null);

        if (userToken == null) {
            log.warn("Page token expires in {} day(s) but no user token is stored to refresh it from; bootstrap required.", daysLeft);
            return;
        }

        String refreshedUserToken = exchangeForLongLivedToken(userToken.getAccessToken());
        saveToken(USER_TOKEN_TYPE, refreshedUserToken, inspectToken(refreshedUserToken).expiresAt());

        String refreshedPageToken = fetchPageToken(refreshedUserToken);
        saveToken(PAGE_TOKEN_TYPE, refreshedPageToken, inspectToken(refreshedPageToken).expiresAt());

        log.info("Facebook tokens renewed successfully.");
    }

    private String exchangeForLongLivedToken(String token) {
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", props.clientId())
                        .queryParam("client_secret", props.clientSecret())
                        .queryParam("fb_exchange_token", token)
                        .build())
                .retrieve()
                .body(String.class);

        return mapper.readValue(json, TokenExchangeResponse.class).access_token();
    }

    private String fetchPageToken(String userLongLivedToken) {
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + props.pageId())
                        .queryParam("fields", "access_token")
                        .queryParam("access_token", userLongLivedToken)
                        .build())
                .retrieve()
                .body(String.class);

        return mapper.readValue(json, PageTokenResponse.class).access_token();
    }

    private TokenInfo inspectToken(String token) {
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/debug_token")
                        .queryParam("input_token", token)
                        .queryParam("access_token", props.clientId() + "|" + props.clientSecret())
                        .build())
                .retrieve()
                .body(String.class);

        DebugTokenResponse.DebugTokenData data = Objects.requireNonNull(
                mapper.readValue(json, DebugTokenResponse.class)).data();

        if (!data.is_valid()) {
            throw new IllegalStateException("Invalid token");
        }

        Long expiresAtEpoch = data.expires_at();
        LocalDateTime expiresAt = expiresAtEpoch != null && expiresAtEpoch > 0
                ? LocalDateTime.ofEpochSecond(expiresAtEpoch, 0, ZoneOffset.UTC)
                : LocalDateTime.now().plusYears(10); // permanent token

        return new TokenInfo(expiresAt);
    }

    private void saveToken(String tokenType, String accessToken, LocalDateTime expiresAt) {
        FacebookToken entity = tokenRepository
                .findTopByTokenTypeOrderByUpdatedAtDesc(tokenType)
                .orElse(new FacebookToken());
        entity.setAccessToken(accessToken);
        entity.setTokenType(tokenType);
        entity.setExpiresAt(expiresAt);
        tokenRepository.save(entity);
    }

    private static String preview(String token) {
        return token.length() <= 10 ? token : token.substring(0, 10) + "...";
    }

    record TokenInfo(LocalDateTime expiresAt) {
    }

    record TokenExchangeResponse(String access_token, String token_type, Long expires_in) {
    }

    record PageTokenResponse(String access_token, String id) {
    }

    record DebugTokenResponse(DebugTokenData data) {
        record DebugTokenData(boolean is_valid, Long expires_at) {
        }
    }

    record TokenStatus(String tokenPreview, LocalDateTime expiresAt, long daysRemaining) {
    }
}
