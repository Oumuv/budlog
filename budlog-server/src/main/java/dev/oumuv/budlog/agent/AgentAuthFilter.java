package dev.oumuv.budlog.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oumuv.budlog.common.ApiResponse;
import dev.oumuv.budlog.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** A separate, read-only credential namespace. Never accepts the family password. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AgentAuthFilter extends OncePerRequestFilter implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(AgentAuthFilter.class);
    private static final String PREFIX = "/api/v1/agent/v1/";
    private static final Pattern CLIENT_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private final AppProperties properties;
    private final ObjectMapper mapper;
    private Map<String, byte[]> clients = Collections.emptyMap();

    public AgentAuthFilter(AppProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getAgent().isEnabled()) {
            return;
        }
        if (properties.getAgent().getMaxRangeDays() < 1 || properties.getAgent().getMaxRangeDays() > 90) {
            throw new IllegalStateException("Agent max-range-days must be between 1 and 90");
        }
        try {
            Map<String, String> configured = mapper.readValue(properties.getAgent().getClientsJson(),
                    new TypeReference<Map<String, String>>() {});
            if (configured == null || configured.isEmpty()) {
                throw new IllegalStateException("Agent enabled without client digests");
            }
            Map<String, byte[]> parsed = new LinkedHashMap<String, byte[]>();
            for (Map.Entry<String, String> client : configured.entrySet()) {
                if (client.getKey() == null || !CLIENT_ID.matcher(client.getKey()).matches()
                        || "unauthenticated".equals(client.getKey())
                        || client.getValue() == null || !SHA256.matcher(client.getValue()).matches()) {
                    throw new IllegalStateException("Invalid Agent client id or SHA-256 digest configuration");
                }
                parsed.put(client.getKey(), decodeHex(client.getValue()));
            }
            clients = Collections.unmodifiableMap(parsed);
        } catch (IOException exception) {
            // Jackson exceptions may embed source snippets; do not echo the configured digests.
            throw new IllegalStateException("Invalid Agent client digest configuration");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // MVC strips semicolon matrix parameters while matching routes. Use the same
        // normalization to ensure a non-canonical URL cannot fall through to family auth.
        return !canonicalPath(decodedPath(request.getRequestURI())).startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String clientId = "unauthenticated";
        response.setHeader("Cache-Control", "no-store");
        try {
            if (!request.getRequestURI().equals(canonicalPath(decodedPath(request.getRequestURI())))) {
                reject(response, 404, 40400, "接口不可用");
                return;
            }
            if (!properties.getAgent().isEnabled()) {
                reject(response, 404, 40400, "接口不可用");
                return;
            }
            // Fail before routing: no write method is delegated to MVC or another filter.
            if (!"GET".equals(request.getMethod())) {
                reject(response, 405, 40500, "仅允许只读 GET 请求");
                return;
            }
            String bearer = request.getHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() > 256) {
                reject(response, 401, 40100, "Agent 身份验证失败");
                return;
            }
            String token = bearer.substring(7);
            if (token.length() < 43 || token.indexOf(' ') >= 0 || token.indexOf('\t') >= 0) {
                reject(response, 401, 40100, "Agent 身份验证失败");
                return;
            }
            byte[] candidate = sha256(token);
            // No client name is supplied in a header or URL: match all configured digests.
            for (Map.Entry<String, byte[]> client : clients.entrySet()) {
                if (MessageDigest.isEqual(client.getValue(), candidate)) {
                    clientId = client.getKey();
                }
            }
            if ("unauthenticated".equals(clientId)) {
                reject(response, 401, 40100, "Agent 身份验证失败");
                return;
            }
            request.setAttribute("agentClientId", clientId);
            chain.doFilter(request, response);
        } finally {
            // No header, query string, response body or exception details in the audit line.
            log.info("agent_request clientId={} path={} status={} durationMs={}", clientId,
                    auditedPath(request.getRequestURI()), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    private String decodedPath(String path) {
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException | java.io.UnsupportedEncodingException exception) {
            return path;
        }
    }

    private String canonicalPath(String path) {
        return path.replaceAll(";[^/]*", "");
    }

    private String auditedPath(String path) {
        switch (path) {
            case PREFIX + "profile":
            case PREFIX + "feedings":
            case PREFIX + "feedings/daily":
            case PREFIX + "feedings/trends":
            case PREFIX + "care/daily":
            case PREFIX + "daily-report":
            case PREFIX + "weights/trends":
                return path;
            default:
                return PREFIX + "unknown";
        }
    }

    private void reject(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), new ApiResponse<Void>(code, message, null));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private byte[] decodeHex(String text) {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(text.charAt(index * 2), 16);
            int low = Character.digit(text.charAt(index * 2 + 1), 16);
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
