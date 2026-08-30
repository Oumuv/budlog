package dev.oumuv.budlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Access access = new Access();
    private final Feeding feeding = new Feeding();
    private String timezone = "Asia/Shanghai";
    private String corsAllowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public Access getAccess() {
        return access;
    }

    public Feeding getFeeding() {
        return feeding;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public static class Access {
        private boolean enabled = true;
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Feeding {
        private int defaultIntervalMinutes = 180;

        public int getDefaultIntervalMinutes() {
            return defaultIntervalMinutes;
        }

        public void setDefaultIntervalMinutes(int defaultIntervalMinutes) {
            this.defaultIntervalMinutes = defaultIntervalMinutes;
        }
    }
}

