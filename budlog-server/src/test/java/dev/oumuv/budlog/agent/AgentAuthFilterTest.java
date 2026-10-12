package dev.oumuv.budlog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oumuv.budlog.access.AccessPasswordFilter;
import dev.oumuv.budlog.access.AccessPasswordService;
import dev.oumuv.budlog.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAuthFilterTest {
    private static final String PATH = "/api/v1/agent/v1/profile";
    private static final String TOKEN = "a-very-long-random-looking-test-token-that-is-not-a-real-secret-123";

    @Test
    void protectsEvenWhenFamilyAccessDisabledAndRejectsWrites() throws Exception {
        AppProperties properties = properties(true);
        properties.getAccess().setEnabled(false);
        AgentAuthFilter agent = agent(properties);
        assertStatus(agent, "GET", PATH, null, 401);
        assertStatus(agent, "GET", PATH, "Bearer wrong-but-long-random-looking-token-that-is-not-authorized", 401);
        for (String method : Arrays.asList("POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
            assertStatus(agent, method, PATH, "Bearer " + TOKEN, 405);
        }
        assertStatus(agent, "GET", PATH, "Bearer " + TOKEN, 200);
        AccessPasswordFilter family = new AccessPasswordFilter(
                new AccessPasswordService(properties, Clock.systemUTC()), mapper());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        family.doFilter(request("GET", "/api/v1/dashboard", "Bearer " + TOKEN), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void credentialsNeverCrossBetweenFamilyAndAgentRoutes() throws Exception {
        AppProperties properties = properties(true);
        properties.getAccess().setEnabled(true);
        properties.getAccess().setPassword("family-secret");
        AgentAuthFilter agent = agent(properties);
        AccessPasswordFilter family = new AccessPasswordFilter(new AccessPasswordService(properties, Clock.systemUTC()), mapper());
        MockHttpServletRequest agentRequest = request("GET", PATH, null);
        agentRequest.addHeader("X-App-Password", "family-secret");
        MockFilterChain agentChain = new MockFilterChain();
        agent.doFilter(agentRequest, new MockHttpServletResponse(), agentChain);
        assertThat(agentChain.getRequest()).isNull();

        MockHttpServletRequest familyRequest = request("GET", "/api/v1/feedings", "Bearer " + TOKEN);
        MockHttpServletResponse familyResponse = new MockHttpServletResponse();
        MockFilterChain familyChain = new MockFilterChain();
        family.doFilter(familyRequest, familyResponse, familyChain);
        assertThat(familyResponse.getStatus()).isEqualTo(401);
        assertThat(familyChain.getRequest()).isNull();

        MockHttpServletRequest bypass = request("GET", PATH, null);
        MockFilterChain bypassChain = new MockFilterChain();
        family.doFilter(bypass, new MockHttpServletResponse(), bypassChain);
        assertThat(bypassChain.getRequest()).isSameAs(bypass);
        assertStatus(agent, "GET", PATH, "Bearer " + TOKEN, 200);
    }

    @Test
    void rejectsMatrixPathEvenWithValidAgentOrFamilyCredentials() throws Exception {
        AppProperties properties = properties(true);
        AgentAuthFilter agent = agent(properties);
        for (String path : Arrays.asList("/api/v1/agent/v1;foo/profile", "/api/v1/agent/v1/profile;foo", "/api/v1/agent/v1%3Bfoo/profile")) {
            assertStatus(agent, "GET", path, "Bearer " + TOKEN, 404);
            assertStatus(agent, "GET", path, null, 404);
        }
    }

    @Test
    void disabledRevokedAndMisconfiguredFailClosed() throws Exception {
        AppProperties disabled = properties(false);
        assertStatus(agent(disabled), "GET", PATH, "Bearer " + TOKEN, 404);
        AppProperties revoked = properties(true);
        revoked.getAgent().setClientsJson("{\"another\":\"" + hex("new-token-not-the-old-one".getBytes(StandardCharsets.UTF_8)) + "\"}");
        assertStatus(agent(revoked), "GET", PATH, "Bearer " + TOKEN, 401);
        AppProperties bad = properties(true);
        bad.getAgent().setClientsJson("{}");
        assertThatThrownBy(() -> agent(bad)).isInstanceOf(IllegalStateException.class);
    }

    private void assertStatus(AgentAuthFilter agent, String method, String path, String authorization, int status) throws Exception {
        MockHttpServletRequest request = request(method, path, authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        agent.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(chain.getRequest() == null).isEqualTo(status != 200);
        assertThat(response.getContentAsString()).doesNotContain(TOKEN);
    }

    private MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }

    private AgentAuthFilter agent(AppProperties properties) {
        AgentAuthFilter filter = new AgentAuthFilter(properties, mapper());
        filter.afterSingletonsInstantiated();
        return filter;
    }

    private AppProperties properties(boolean enabled) throws Exception {
        AppProperties properties = new AppProperties();
        properties.getAgent().setEnabled(enabled);
        properties.getAgent().setClientsJson("{\"generic-client\":\"" + hex(TOKEN.getBytes(StandardCharsets.UTF_8)) + "\"}");
        return properties;
    }

    private String hex(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte b : hash) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }

    private ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
}
