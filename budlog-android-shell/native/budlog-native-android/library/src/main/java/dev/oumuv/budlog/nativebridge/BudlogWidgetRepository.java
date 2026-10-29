package dev.oumuv.budlog.nativebridge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BudlogWidgetRepository {
    private static final String PREFS_NAME = "budlog_widget_state";
    private static final String KEY_SUMMARY_JSON = "summary_json";
    private static final String KEY_SUMMARY_UPDATED_AT = "summary_updated_at";
    private static final String KEY_AUTH_REQUIRED = "auth_required";
    private static final String KEY_REFRESH_IN_PROGRESS = "refresh_in_progress";
    private static final String KEY_OFFLINE = "offline";
    private static final String KEY_UNSUPPORTED = "unsupported";
    private static final String KEY_TIMER_START_TIME = "timer_start_time";
    private static final String KEY_TIMER_BREAST_SIDE = "timer_breast_side";
    private static final Pattern ISO_TIME = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:?\\d{2})$");

    private final SharedPreferences preferences;

    BudlogWidgetRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    WidgetState readState() {
        Summary summary = null;
        String json = preferences.getString(KEY_SUMMARY_JSON, "");
        if (json != null && json.length() > 0) {
            try {
                summary = Summary.fromJson(new JSONObject(json));
            } catch (JSONException ignored) {
                preferences.edit().remove(KEY_SUMMARY_JSON).remove(KEY_SUMMARY_UPDATED_AT).apply();
            }
        }
        return new WidgetState(
                summary,
                preferences.getLong(KEY_SUMMARY_UPDATED_AT, 0L),
                preferences.getBoolean(KEY_AUTH_REQUIRED, false),
                preferences.getBoolean(KEY_REFRESH_IN_PROGRESS, false),
                preferences.getBoolean(KEY_OFFLINE, false),
                preferences.getBoolean(KEY_UNSUPPORTED, false),
                new TimerState(
                        preferences.getLong(KEY_TIMER_START_TIME, 0L),
                        preferences.getString(KEY_TIMER_BREAST_SIDE, "")));
    }

    void saveSummary(JSONObject summary, long updatedAt) {
        preferences.edit()
                .putString(KEY_SUMMARY_JSON, summary.toString())
                .putLong(KEY_SUMMARY_UPDATED_AT, updatedAt)
                .putBoolean(KEY_AUTH_REQUIRED, false)
                .putBoolean(KEY_REFRESH_IN_PROGRESS, false)
                .putBoolean(KEY_OFFLINE, false)
                .putBoolean(KEY_UNSUPPORTED, false)
                .apply();
    }

    void markRefreshStarted() {
        preferences.edit().putBoolean(KEY_REFRESH_IN_PROGRESS, true).apply();
    }

    void markNetworkFailure() {
        preferences.edit()
                .putBoolean(KEY_REFRESH_IN_PROGRESS, false)
                .putBoolean(KEY_OFFLINE, true)
                .apply();
    }

    void markAuthRequired() {
        preferences.edit()
                .putBoolean(KEY_REFRESH_IN_PROGRESS, false)
                .putBoolean(KEY_AUTH_REQUIRED, true)
                .putBoolean(KEY_OFFLINE, false)
                .apply();
    }

    void markAuthRestored() {
        preferences.edit()
                .putBoolean(KEY_AUTH_REQUIRED, false)
                .putBoolean(KEY_UNSUPPORTED, false)
                .apply();
    }

    void markUnsupported() {
        preferences.edit()
                .putBoolean(KEY_REFRESH_IN_PROGRESS, false)
                .putBoolean(KEY_UNSUPPORTED, true)
                .putBoolean(KEY_OFFLINE, false)
                .apply();
    }

    void syncTimer(long startTime, String breastSide) {
        preferences.edit()
                .putLong(KEY_TIMER_START_TIME, startTime)
                .putString(KEY_TIMER_BREAST_SIDE, breastSide)
                .apply();
    }

    void clearTimer() {
        preferences.edit().remove(KEY_TIMER_START_TIME).remove(KEY_TIMER_BREAST_SIDE).apply();
    }

    void clearAll() {
        preferences.edit().clear().apply();
    }

    static long parseIsoMillis(String value) {
        if (value == null) return -1L;
        Matcher matcher = ISO_TIME.matcher(value);
        if (!matcher.matches()) return -1L;

        String fraction = matcher.group(2);
        String milliseconds = fraction == null ? "000" : (fraction + "000").substring(0, 3);
        String offset = matcher.group(3);
        if ("Z".equals(offset)) {
            offset = "+0000";
        } else if (offset.indexOf(':') >= 0) {
            offset = offset.substring(0, 3) + offset.substring(4);
        }

        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return parser.parse(matcher.group(1) + "." + milliseconds + offset).getTime();
        } catch (ParseException exception) {
            return -1L;
        }
    }

    static final class WidgetState {
        final Summary summary;
        final long summaryUpdatedAt;
        final boolean authRequired;
        final boolean refreshing;
        final boolean offline;
        final boolean unsupported;
        final TimerState timer;

        WidgetState(
                Summary summary,
                long summaryUpdatedAt,
                boolean authRequired,
                boolean refreshing,
                boolean offline,
                boolean unsupported,
                TimerState timer) {
            this.summary = summary;
            this.summaryUpdatedAt = summaryUpdatedAt;
            this.authRequired = authRequired;
            this.refreshing = refreshing;
            this.offline = offline;
            this.unsupported = unsupported;
            this.timer = timer;
        }
    }

    static final class TimerState {
        final long startTime;
        final String breastSide;

        TimerState(long startTime, String breastSide) {
            this.startTime = startTime;
            this.breastSide = breastSide == null ? "" : breastSide;
        }

        boolean isActive(long now) {
            return startTime > 0L && startTime <= now + 5L * 60L * 1000L
                    && startTime >= now - 24L * 60L * 60L * 1000L;
        }
    }

    static final class Summary {
        final boolean configured;
        final String babyName;
        final long lastFeedingTime;
        final long nextExpectedFeedingTime;
        final long lastPeeTime;
        final long lastPoopTime;
        final int feedingCount;
        final double bottleAmountMl;
        final int peeCount;
        final int poopCount;
        final int milkStorageCount;
        final double storedMilkAmountMl;

        Summary(
                boolean configured,
                String babyName,
                long lastFeedingTime,
                long nextExpectedFeedingTime,
                long lastPeeTime,
                long lastPoopTime,
                int feedingCount,
                double bottleAmountMl,
                int peeCount,
                int poopCount,
                int milkStorageCount,
                double storedMilkAmountMl) {
            this.configured = configured;
            this.babyName = babyName;
            this.lastFeedingTime = lastFeedingTime;
            this.nextExpectedFeedingTime = nextExpectedFeedingTime;
            this.lastPeeTime = lastPeeTime;
            this.lastPoopTime = lastPoopTime;
            this.feedingCount = feedingCount;
            this.bottleAmountMl = bottleAmountMl;
            this.peeCount = peeCount;
            this.poopCount = poopCount;
            this.milkStorageCount = milkStorageCount;
            this.storedMilkAmountMl = storedMilkAmountMl;
        }

        static Summary fromJson(JSONObject json) throws JSONException {
            JSONObject today = json.getJSONObject("today");
            return new Summary(
                    json.getBoolean("configured"),
                    nullableString(json, "babyName"),
                    nullableTime(json, "lastFeedingTime"),
                    nullableTime(json, "nextExpectedFeedingTime"),
                    nullableTime(json, "lastPeeTime"),
                    nullableTime(json, "lastPoopTime"),
                    today.getInt("feedingCount"),
                    today.getDouble("bottleAmountMl"),
                    today.getInt("peeCount"),
                    today.getInt("poopCount"),
                    today.getInt("milkStorageCount"),
                    today.getDouble("storedMilkAmountMl"));
        }

        private static String nullableString(JSONObject json, String key) throws JSONException {
            return json.isNull(key) ? "" : json.getString(key);
        }

        private static long nullableTime(JSONObject json, String key) throws JSONException {
            if (json.isNull(key)) return 0L;
            long value = parseIsoMillis(json.getString(key));
            if (value < 0L) throw new JSONException("Invalid widget timestamp.");
            return value;
        }
    }
}
