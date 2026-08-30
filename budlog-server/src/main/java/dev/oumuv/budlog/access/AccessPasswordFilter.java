package dev.oumuv.budlog.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AccessPasswordFilter extends OncePerRequestFilter {

    private final AccessPasswordService passwordService;
    private final ObjectMapper objectMapper;

    public AccessPasswordFilter(AccessPasswordService passwordService, ObjectMapper objectMapper) {
        this.passwordService = passwordService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || "/api/v1/access/verify".equals(path)
                || "/actuator/health".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (passwordService.matches(request.getHeader(AccessPasswordService.HEADER_NAME))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiResponse<Void>(40100, "家庭访问密码不正确", null));
    }
}
