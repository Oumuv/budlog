package dev.oumuv.budlog.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oumuv.budlog.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPasswordFilterTest {

    private AccessPasswordService service;
    private AccessPasswordFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getAccess().setEnabled(true);
        properties.getAccess().setPassword("family-secret");
        service = new AccessPasswordService(
                properties,
                Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC));
        filter = new AccessPasswordFilter(service, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void widgetCookieOnlyAuthorizesTheSummaryGetEndpoint() throws Exception {
        String token = cookieValue(service.createWidgetSessionCookie(new MockHttpServletRequest()));

        assertAuthorized("GET", "/api/v1/widget/summary", AccessPasswordService.WIDGET_COOKIE_NAME, token);
        assertRejected("POST", "/api/v1/widget/summary", AccessPasswordService.WIDGET_COOKIE_NAME, token);
        assertRejected("GET", "/api/v1/dashboard", AccessPasswordService.WIDGET_COOKIE_NAME, token);
        assertRejected("GET", "/api/v1/widget/other", AccessPasswordService.WIDGET_COOKIE_NAME, token);
    }

    @Test
    void mediaCookieCannotReadWidgetSummary() throws Exception {
        String token = cookieValue(service.createMediaSessionCookie(new MockHttpServletRequest()));
        assertRejected("GET", "/api/v1/widget/summary", AccessPasswordService.MEDIA_COOKIE_NAME, token);
    }

    private void assertAuthorized(String method, String path, String name, String token) throws Exception {
        MockHttpServletRequest request = request(method, path, name, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertRejected(String method, String path, String name, String token) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(method, path, name, token), response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MockHttpServletRequest request(String method, String path, String name, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setCookies(new Cookie(name, token));
        return request;
    }

    private String cookieValue(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }
}
