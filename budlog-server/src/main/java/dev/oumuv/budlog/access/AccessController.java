package dev.oumuv.budlog.access;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {

    private final AccessPasswordService passwordService;

    public AccessController(AccessPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(
            @RequestHeader(value = AccessPasswordService.HEADER_NAME, required = false) String password,
            HttpServletRequest request) {
        if (!passwordService.matches(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String cookie = passwordService.createMediaSessionCookie(request);
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (cookie != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return response.build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, passwordService.clearMediaSessionCookie(request))
                .build();
    }
}
