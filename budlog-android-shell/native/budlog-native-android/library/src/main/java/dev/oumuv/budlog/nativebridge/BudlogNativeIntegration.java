package dev.oumuv.budlog.nativebridge;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BudlogNativeIntegration {
    static final String PREFS_NAME = "budlog_native_bridge";
    static final String PREF_ALLOWED_ORIGIN = "allowed_origin";
    static final String EXTRA_SHORTCUT_ROUTE = "budlog_shortcut_route";

    private static final int NOTIFICATION_PERMISSION_REQUEST = 9401;
    private static final int MAX_COMMAND_LENGTH = 64 * 1024;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final Set<String> SIMPLE_ACTIONS = new HashSet<>(Arrays.asList(
            "resume", "pause", "previous", "next", "stop"
    ));
    private static final Set<String> SHORTCUT_ROUTES = new HashSet<>(Arrays.asList(
            "/pages/feeding/index?mode=timer",
            "/pages/feeding/index?mode=bottle",
            "/pages/diaper/index",
            "/pages/white-noise/index"
    ));

    private BudlogNativeIntegration() {
    }

    public static boolean initialize(Activity activity, String allowedOrigin) {
        if (activity == null) return false;
        String normalizedOrigin = normalizeOrigin(allowedOrigin);
        if (normalizedOrigin == null) return false;

        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_ALLOWED_ORIGIN, normalizedOrigin)
                .apply();
        installShortcuts(activity);
        BudlogMediaService.ensureNotificationChannel(activity);
        requestNotificationPermission(activity);
        return true;
    }

    public static String dispatch(Context context, String commandJson) {
        if (context == null) return error("Native context is unavailable.");
        if (commandJson == null || commandJson.length() == 0 || commandJson.length() > MAX_COMMAND_LENGTH) {
            return error("Invalid native media command size.");
        }

        try {
            JSONObject input = new JSONObject(commandJson);
            JSONObject command = sanitizeCommand(context, input);
            String action = command.getString("action");
            Intent intent = new Intent(context, BudlogMediaService.class)
                    .setAction(BudlogMediaService.ACTION_COMMAND)
                    .putExtra(BudlogMediaService.EXTRA_COMMAND, command.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && ("play".equals(action) || "resume".equals(action))) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return new JSONObject().put("ok", true).toString();
        } catch (Exception exception) {
            return error(exception.getMessage() == null ? "Invalid native media command." : exception.getMessage());
        }
    }

    public static String getState() {
        return BudlogMediaService.snapshot();
    }

    public static String consumeShortcutRoute(Activity activity) {
        if (activity == null) return "";
        Intent intent = activity.getIntent();
        if (intent == null) return "";
        String route = intent.getStringExtra(EXTRA_SHORTCUT_ROUTE);
        intent.removeExtra(EXTRA_SHORTCUT_ROUTE);
        return route != null && SHORTCUT_ROUTES.contains(route) ? route : "";
    }

    private static JSONObject sanitizeCommand(Context context, JSONObject input) throws JSONException {
        String action = boundedString(input, "action", 24);
        JSONObject output = new JSONObject().put("action", action);
        if (SIMPLE_ACTIONS.contains(action)) return output;

        if ("seek".equals(action)) {
            double position = input.getDouble("position");
            if (!Double.isFinite(position) || position < 0 || position > 24 * 60 * 60) {
                throw new JSONException("Invalid seek position.");
            }
            return output.put("position", position);
        }
        if ("setLoop".equals(action)) {
            return output.put("loop", input.getBoolean("loop"));
        }
        if ("setSleepTimer".equals(action)) {
            if (input.isNull("deadline")) return output.put("deadline", JSONObject.NULL);
            long deadline = input.getLong("deadline");
            long now = System.currentTimeMillis();
            if (deadline < now || deadline > now + 720L * 60L * 1000L) {
                throw new JSONException("Invalid sleep timer deadline.");
            }
            return output.put("deadline", deadline);
        }
        if (!"play".equals(action)) {
            throw new JSONException("Unsupported native media action.");
        }

        String origin = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_ALLOWED_ORIGIN, "");
        if (origin.length() == 0) throw new JSONException("Native media bridge is not initialized.");
        JSONArray queue = input.getJSONArray("queue");
        if (queue.length() == 0 || queue.length() > MAX_QUEUE_SIZE) {
            throw new JSONException("Invalid native media queue size.");
        }
        JSONArray sanitizedQueue = new JSONArray();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.getJSONObject(i);
            String url = boundedString(item, "url", 2048);
            if (!isAllowedStreamUrl(origin, url)) throw new JSONException("Rejected native media URL.");
            sanitizedQueue.put(new JSONObject()
                    .put("id", boundedString(item, "id", 128))
                    .put("title", boundedString(item, "title", 160))
                    .put("playlist", boundedString(item, "playlist", 160))
                    .put("url", url));
        }
        int index = input.getInt("index");
        if (index < 0 || index >= sanitizedQueue.length()) throw new JSONException("Invalid queue index.");
        output.put("queue", sanitizedQueue);
        output.put("index", index);
        output.put("loop", input.optBoolean("loop", true));
        return output;
    }

    private static String boundedString(JSONObject object, String key, int maxLength) throws JSONException {
        String value = object.getString(key).trim();
        if (value.length() == 0 || value.length() > maxLength) {
            throw new JSONException("Invalid " + key + ".");
        }
        return value;
    }

    private static boolean isAllowedStreamUrl(String origin, String value) {
        try {
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return false;
            }
            String candidateOrigin = buildOrigin(uri);
            String path = uri.getEncodedPath();
            return origin.equals(candidateOrigin)
                    && path != null
                    && path.matches("^/api/v1/white-noise/tracks/[^/]{1,128}/stream$")
                    && uri.getFragment() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String normalizeOrigin(String value) {
        if (value == null || !value.equals(value.trim())) return null;
        try {
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && uri.getPath().length() > 0)) {
                return null;
            }
            return buildOrigin(uri);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String buildOrigin(Uri uri) {
        int port = uri.getPort();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return port == -1 || port == 443 ? "https://" + host : "https://" + host + ":" + port;
    }

    private static void installShortcuts(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        List<ShortcutInfo> shortcuts = new ArrayList<>();
        shortcuts.add(shortcut(activity, "feeding_timer", "喂奶计时", "/pages/feeding/index?mode=timer", 0,
                drawableResource(activity, "ic_shortcut_timer", android.R.drawable.ic_menu_recent_history)));
        shortcuts.add(shortcut(activity, "feeding_bottle", "记录瓶喂", "/pages/feeding/index?mode=bottle", 1,
                drawableResource(activity, "ic_shortcut_bottle", android.R.drawable.ic_input_add)));
        shortcuts.add(shortcut(activity, "diaper", "记录尿便", "/pages/diaper/index", 2,
                drawableResource(activity, "ic_shortcut_diaper", android.R.drawable.ic_menu_edit)));
        shortcuts.add(shortcut(activity, "white_noise", "播放白噪音", "/pages/white-noise/index", 3,
                drawableResource(activity, "ic_shortcut_white_noise", android.R.drawable.ic_media_play)));
        int shortcutCount = Math.min(shortcuts.size(), manager.getMaxShortcutCountPerActivity());
        if (shortcutCount == 0) return;
        try {
            manager.setDynamicShortcuts(shortcuts.subList(0, shortcutCount));
        } catch (IllegalArgumentException ignored) {
            // Some launchers expose fewer shortcut slots; the app remains usable without them.
        }
    }

    private static int drawableResource(Activity activity, String name, int fallback) {
        int resource = activity.getResources().getIdentifier(name, "drawable", activity.getPackageName());
        return resource == 0 ? fallback : resource;
    }

    private static ShortcutInfo shortcut(
            Activity activity,
            String id,
            String label,
            String route,
            int rank,
            int iconResource
    ) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setClass(activity, activity.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SHORTCUT_ROUTE, route);
        return new ShortcutInfo.Builder(activity, id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setRank(rank)
                .setIcon(Icon.createWithResource(activity, iconResource))
                .setIntent(intent)
                .build();
    }

    private static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message).toString();
        } catch (JSONException ignored) {
            return "{\"ok\":false,\"error\":\"Native media command failed.\"}";
        }
    }
}
