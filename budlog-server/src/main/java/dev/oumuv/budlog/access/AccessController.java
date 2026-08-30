package dev.oumuv.budlog.access;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {

    private final AccessPasswordService passwordService;

    public AccessController(AccessPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(
            @RequestHeader(value = AccessPasswordService.HEADER_NAME, required = false) String password) {
        return passwordService.matches(password)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}

