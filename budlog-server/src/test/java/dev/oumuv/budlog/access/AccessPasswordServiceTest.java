package dev.oumuv.budlog.access;

import dev.oumuv.budlog.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPasswordServiceTest {

    private AccessPasswordService service;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getAccess().setEnabled(true);
        properties.getAccess().setPassword("family-secret");
        service = new AccessPasswordService(
                properties,
                Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC));
        request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
    }

    @Test
    void createsPurposeBoundSecureCookies() {
        String mediaCookie = service.createMediaSessionCookie(request);
        String widgetCookie = service.createWidgetSessionCookie(request);
        String mediaToken = cookieValue(mediaCookie);
        String widgetToken = cookieValue(widgetCookie);

        assertThat(mediaCookie).contains("Path=/api/v1/white-noise", "Secure", "HttpOnly", "SameSite=Strict");
        assertThat(widgetCookie).contains(
                "Path=/api/v1/widget",
                "Max-Age=604800",
                "Secure",
                "HttpOnly",
                "SameSite=Strict");
        assertThat(service.matchesMediaSession(mediaToken)).isTrue();
        assertThat(service.matchesWidgetSession(widgetToken)).isTrue();
        assertThat(service.matchesMediaSession(widgetToken)).isFalse();
        assertThat(service.matchesWidgetSession(mediaToken)).isFalse();
    }

    @Test
    void widgetCookieRemainsSecureWhenBackendReceivesHttp() {
        request.removeHeader("X-Forwarded-Proto");
        assertThat(service.createWidgetSessionCookie(request)).contains("; Secure");
        assertThat(service.clearWidgetSessionCookie(request)).contains("; Secure");
    }

    @Test
    void rejectsTamperedWidgetSession() {
        String token = cookieValue(service.createWidgetSessionCookie(request));
        assertThat(service.matchesWidgetSession(token + "x")).isFalse();
    }

    @Test
    void clearsBothCookiesAtTheirOwnPaths() {
        assertThat(service.clearMediaSessionCookie(request))
                .contains("budlog_media_session=", "Path=/api/v1/white-noise", "Max-Age=0");
        assertThat(service.clearWidgetSessionCookie(request))
                .contains("budlog_widget_session=", "Path=/api/v1/widget", "Max-Age=0");
    }

    private String cookieValue(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }
}
