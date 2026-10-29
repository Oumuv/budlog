package dev.oumuv.budlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Access access = new Access();
    private final Feeding feeding = new Feeding();
    private final WhiteNoise whiteNoise = new WhiteNoise();
    private final Agent agent = new Agent();
    private String timezone = "Asia/Shanghai";
    private String corsAllowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public Access getAccess() {
        return access;
    }

    public Feeding getFeeding() {
        return feeding;
    }

    public WhiteNoise getWhiteNoise() {
        return whiteNoise;
    }

    public Agent getAgent() {
        return agent;
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

    public static class Agent {
        private boolean enabled;
        private int maxRangeDays = 90;
        // JSON object of clientId -> SHA-256 hex digest, supplied via protected environment.
        private String clientsJson = "{}";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRangeDays() {
            return maxRangeDays;
        }

        public void setMaxRangeDays(int maxRangeDays) {
            this.maxRangeDays = maxRangeDays;
        }

        public String getClientsJson() {
            return clientsJson;
        }

        public void setClientsJson(String clientsJson) {
            this.clientsJson = clientsJson;
        }
    }

    public static class WhiteNoise {
        private String rootDirectory = "/data/white-noise";

        public String getRootDirectory() {
            return rootDirectory;
        }

        public void setRootDirectory(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }
    }
}
