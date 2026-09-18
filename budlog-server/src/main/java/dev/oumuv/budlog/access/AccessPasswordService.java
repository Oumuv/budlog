package dev.oumuv.budlog.access;

import dev.oumuv.budlog.config.AppProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AccessPasswordService implements SmartInitializingSingleton {

    public static final String HEADER_NAME = "X-App-Password";
    public static final String MEDIA_COOKIE_NAME = "budlog_media_session";
    public static final String MEDIA_COOKIE_PATH = "/api/v1/white-noise";
    private static final Duration MEDIA_SESSION_TTL = Duration.ofHours(24);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppProperties properties;
    private final Clock clock;
    private final SecretKeySpec mediaSessionSigningKey;

    public AccessPasswordService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] signingKey = new byte[32];
        new SecureRandom().nextBytes(signingKey);
        this.mediaSessionSigningKey = new SecretKeySpec(signingKey, HMAC_ALGORITHM);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getAccess().isEnabled() && !StringUtils.hasText(properties.getAccess().getPassword())) {
            throw new IllegalStateException("APP_ACCESS_PASSWORD must be configured when access protection is enabled");
        }
    }

    public boolean matches(String candidate) {
        if (!properties.getAccess().isEnabled()) {
            return true;
        }
        if (candidate == null) {
            return false;
        }
        byte[] expected = properties.getAccess().getPassword().getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public boolean matchesMediaSession(String candidate) {
        if (!properties.getAccess().isEnabled()) {
            return true;
        }
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            return false;
        }
        try {
            long expiresAt = Long.parseLong(parts[1]);
            if (expiresAt <= Instant.now(clock).getEpochSecond()) {
                return false;
            }
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String createMediaSessionCookie(HttpServletRequest request) {
        if (!properties.getAccess().isEnabled()) {
            return null;
        }
        long expiresAt = Instant.now(clock).plus(MEDIA_SESSION_TTL).getEpochSecond();
        String payload = "v1." + expiresAt;
        String value = payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        return cookie(value, isSecure(request), null).toString();
    }

    public String clearMediaSessionCookie(HttpServletRequest request) {
        return cookie("", isSecure(request), Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, boolean secure, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(MEDIA_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(MEDIA_COOKIE_PATH);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(mediaSessionSigningKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        } catch (java.security.InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign media session", exception);
        }
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return StringUtils.hasText(forwardedProto)
                && "https".equalsIgnoreCase(forwardedProto.split(",", 2)[0].trim());
    }
}
