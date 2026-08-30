package dev.oumuv.budlog.access;

import dev.oumuv.budlog.config.AppProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AccessPasswordService implements SmartInitializingSingleton {

    public static final String HEADER_NAME = "X-App-Password";

    private final AppProperties properties;

    public AccessPasswordService(AppProperties properties) {
        this.properties = properties;
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
}

