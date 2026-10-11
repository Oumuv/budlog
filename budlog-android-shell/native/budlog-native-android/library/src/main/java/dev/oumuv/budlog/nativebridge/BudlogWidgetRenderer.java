package dev.oumuv.budlog.nativebridge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class BudlogWidgetRenderer {
    private static final int VARIANT_COMPACT = 0;
    private static final int VARIANT_STANDARD = 1;
    private static final int VARIANT_TALL = 2;
    private static final int VARIANT_EXPANDED = 3;
    private static final long STALE_AFTER_MS = 2L * 60L * 60L * 1000L;
    private static final long EXPIRED_AFTER_MS = 24L * 60L * 60L * 1000L;

    private BudlogWidgetRenderer() {
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, BudlogWidgetProvider.class));
        for (int id : ids) update(context, manager, id);
    }

    static void update(Context context, AppWidgetManager manager, int appWidgetId) {
        try {
            BudlogWidgetRepository.WidgetState state = new BudlogWidgetRepository(context).readState();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Map<SizeF, RemoteViews> layouts = new HashMap<SizeF, RemoteViews>();
                layouts.put(new SizeF(110F, 110F), build(context, appWidgetId, VARIANT_COMPACT, state));
                layouts.put(new SizeF(250F, 110F), build(context, appWidgetId, VARIANT_STANDARD, state));
                layouts.put(new SizeF(250F, 180F), build(context, appWidgetId, VARIANT_TALL, state));
                layouts.put(new SizeF(330F, 180F), build(context, appWidgetId, VARIANT_EXPANDED, state));
                manager.updateAppWidget(appWidgetId, new RemoteViews(layouts));
                return;
            }
            int variant = chooseVariant(manager.getAppWidgetOptions(appWidgetId));
            manager.updateAppWidget(appWidgetId, build(context, appWidgetId, variant, state));
        } catch (RuntimeException ignored) {
            // A malformed cached snapshot must not break the launcher's widget host.
        }
    }

    private static int chooseVariant(Bundle options) {
        if (options == null) return VARIANT_STANDARD;
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        if (width < 210) return VARIANT_COMPACT;
        if (height < 180) return VARIANT_STANDARD;
        return width >= 330 ? VARIANT_EXPANDED : VARIANT_TALL;
    }

    private static RemoteViews build(
            Context context,
            int appWidgetId,
            int variant,
            BudlogWidgetRepository.WidgetState state) {
        String layoutName;
        if (variant == VARIANT_COMPACT) {
            layoutName = "budlog_widget_compact";
        } else if (variant == VARIANT_TALL || variant == VARIANT_EXPANDED) {
            layoutName = "budlog_widget_expanded";
        } else {
            layoutName = "budlog_widget_standard";
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), resource(context, "layout", layoutName));
        long now = System.currentTimeMillis();

        applyVariantTextSizes(context, views, variant);
        bindHeader(context, views, variant, state, now);
        bindContent(context, views, variant, state, now);
        bindShortcuts(context, views, appWidgetId, variant, state);
        return views;
    }

    private static void applyVariantTextSizes(Context context, RemoteViews views, int variant) {
        if (variant != VARIANT_TALL) return;
        boolean largeSystemFont = context.getResources().getConfiguration().fontScale >= 1.2F;
        setTextSize(context, views, "widget_title", largeSystemFont ? 11F : 12F);
        setTextSize(context, views, "widget_subtitle", largeSystemFont ? 7F : 8F);
        setTextSize(context, views, "widget_feeding_value", largeSystemFont ? 13F : 16F);
        setTextSize(context, views, "widget_pee_value", largeSystemFont ? 10F : 13F);
        setTextSize(context, views, "widget_milk_value", largeSystemFont ? 12F : 15F);
        float metaSize = largeSystemFont ? 6F : 8F;
        setTextSize(context, views, "widget_feeding_meta", metaSize);
        setTextSize(context, views, "widget_pee_meta", metaSize);
        setTextSize(context, views, "widget_milk_meta", metaSize);
    }

    private static void setTextSize(Context context, RemoteViews views, String viewName, float sizeSp) {
        views.setTextViewTextSize(id(context, viewName), TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    private static void bindHeader(
            Context context,
            RemoteViews views,
            int variant,
            BudlogWidgetRepository.WidgetState state,
            long now) {
        String babyName = state.summary == null ? "" : state.summary.babyName;
        String title = variant == VARIANT_COMPACT || babyName.length() == 0
                ? "Budlog"
                : "Budlog · " + babyName;
        views.setImageViewResource(id(context, "widget_brand_icon"), context.getApplicationInfo().icon);
        views.setTextViewText(id(context, "widget_title"), title);
        views.setTextViewText(id(context, "widget_subtitle"), freshnessText(state, now));
        views.setContentDescription(id(context, "widget_refresh"), state.refreshing ? "正在刷新组件" : "刷新组件数据");
    }

    private static void bindContent(
            Context context,
            RemoteViews views,
            int variant,
            BudlogWidgetRepository.WidgetState state,
            long now) {
        int normal = id(context, "widget_normal_content");
        int timer = id(context, "widget_timer_content");
        int message = id(context, "widget_state_content");
        views.setViewVisibility(normal, View.GONE);
        views.setViewVisibility(timer, View.GONE);
        views.setViewVisibility(message, View.GONE);

        if (state.timer.isActive(now)) {
            views.setViewVisibility(timer, View.VISIBLE);
            long elapsed = Math.max(0L, now - state.timer.startTime);
            long base = SystemClock.elapsedRealtime() - elapsed;
            views.setChronometer(id(context, "widget_timer_value"), base, "%s", true);
            views.setTextViewText(id(context, "widget_timer_side"), sideLabel(state.timer.breastSide));
            views.setTextViewText(id(context, "widget_timer_started"), "开始于 " + timeText(state.timer.startTime));
            return;
        }

        views.setChronometer(id(context, "widget_timer_value"), SystemClock.elapsedRealtime(), "%s", false);
        String stateTitle = stateTitle(state);
        if (stateTitle.length() > 0) {
            views.setViewVisibility(message, View.VISIBLE);
            views.setTextViewText(id(context, "widget_state_title"), stateTitle);
            views.setTextViewText(id(context, "widget_state_copy"), stateCopy(state));
            return;
        }

        views.setViewVisibility(normal, View.VISIBLE);
        BudlogWidgetRepository.Summary summary = state.summary;
        boolean expired = state.summaryUpdatedAt <= 0L || now - state.summaryUpdatedAt >= EXPIRED_AFTER_MS;
        boolean compactTime = variant == VARIANT_COMPACT || variant == VARIANT_TALL;
        views.setTextViewText(id(context, "widget_feeding_value"), expired
                ? "—"
                : formatElapsed(summary.lastFeedingTime, now, compactTime));
        views.setTextViewText(id(context, "widget_feeding_meta"), expired
                ? "打开 App 刷新"
                : expectedFeedingText(summary.nextExpectedFeedingTime, now, compactTime));
        if (variant != VARIANT_COMPACT) {
            views.setTextViewText(id(context, "widget_pee_value"), expired
                    ? "— / —"
                    : diaperElapsed(summary.lastPeeTime, now, variant) + " / "
                    + diaperElapsed(summary.lastPoopTime, now, variant));
            views.setTextViewText(id(context, "widget_pee_meta"),
                    "今日 " + summary.peeCount + " / " + summary.poopCount + " 次");
            views.setTextViewText(id(context, "widget_milk_value"), expired
                    ? "—"
                    : amount(summary.storedMilkAmountMl) + "ml");
            views.setTextViewText(id(context, "widget_milk_meta"),
                    "今日 " + summary.milkStorageCount + " 次");
        }
        views.setViewVisibility(id(context, "widget_summary"), View.GONE);
    }

    private static void bindShortcuts(
            Context context,
            RemoteViews views,
            int appWidgetId,
            int variant,
            BudlogWidgetRepository.WidgetState state) {
        String rootRoute = state.summary != null && !state.summary.configured
                ? "/pages/settings/index"
                : "/pages/home/index";
        views.setOnClickPendingIntent(id(context, "widget_root"), launch(context, rootRoute, requestCode(appWidgetId, 1)));
        views.setOnClickPendingIntent(id(context, "widget_normal_content"), launch(
                context, "/pages/records/index", requestCode(appWidgetId, 2)));
        views.setOnClickPendingIntent(id(context, "widget_timer_content"), launch(
                context, "/pages/feeding/index?mode=timer", requestCode(appWidgetId, 3)));
        views.setOnClickPendingIntent(id(context, "widget_state_content"), launch(
                context, rootRoute, requestCode(appWidgetId, 4)));

        PendingIntent refreshIntent = state.authRequired
                ? launch(context, "/pages/home/index", requestCode(appWidgetId, 5))
                : refresh(context, appWidgetId);
        views.setOnClickPendingIntent(id(context, "widget_refresh"), refreshIntent);

        bindShortcut(context, views, "widget_shortcut_timer", "/pages/feeding/index?mode=timer", appWidgetId, 6, "开始亲喂计时");
        bindShortcut(context, views, "widget_shortcut_bottle", "/pages/feeding/index?mode=bottle", appWidgetId, 7, "记录瓶喂");
        bindShortcut(context, views, "widget_shortcut_diaper", "/pages/diaper/index", appWidgetId, 8, "记录尿便");
        bindShortcut(context, views, "widget_shortcut_milk", "/pages/milk-storage/index", appWidgetId, 9, "记录存奶");

        views.setViewVisibility(id(context, "widget_shortcut_bottle"), variant == VARIANT_COMPACT ? View.GONE : View.VISIBLE);
        views.setViewVisibility(id(context, "widget_shortcut_milk"), variant == VARIANT_COMPACT ? View.GONE : View.VISIBLE);
    }

    private static void bindShortcut(
            Context context,
            RemoteViews views,
            String viewName,
            String route,
            int appWidgetId,
            int offset,
            String description) {
        int viewId = id(context, viewName);
        views.setOnClickPendingIntent(viewId, launch(context, route, requestCode(appWidgetId, offset)));
        views.setContentDescription(viewId, description);
    }

    private static PendingIntent launch(Context context, String route, int requestCode) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) intent = new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(BudlogNativeIntegration.EXTRA_SHORTCUT_ROUTE, route);
        return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags());
    }

    private static PendingIntent refresh(Context context, int appWidgetId) {
        Intent intent = new Intent(context, BudlogWidgetProvider.class)
                .setAction(BudlogWidgetProvider.ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(context, requestCode(appWidgetId, 20), intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private static int requestCode(int appWidgetId, int offset) {
        return 20_000 + (appWidgetId & 0x3ffff) * 32 + offset;
    }

    private static String stateTitle(BudlogWidgetRepository.WidgetState state) {
        if (state.authRequired) return "需要重新验证";
        if (state.unsupported) return "暂不支持同步";
        if (state.summary == null) return "打开 Budlog 完成初始化";
        if (!state.summary.configured) return "先完善宝宝资料";
        return "";
    }

    private static String stateCopy(BudlogWidgetRepository.WidgetState state) {
        if (state.authRequired) return "打开 App 后将自动恢复";
        if (state.unsupported) return "快捷入口仍可正常使用";
        if (state.summary != null && !state.summary.configured) return "保存资料后即可显示照护状态";
        return "登录并打开首页后同步数据";
    }

    private static String freshnessText(BudlogWidgetRepository.WidgetState state, long now) {
        if (state.authRequired) return "打开 App 重新验证";
        if (state.unsupported) return "服务端暂不支持组件同步";
        if (state.refreshing) return "正在刷新";
        if (state.summaryUpdatedAt <= 0L) return "尚未同步";
        if (state.offline) return "离线 · 更新于 " + timeText(state.summaryUpdatedAt);
        long age = Math.max(0L, now - state.summaryUpdatedAt);
        if (age < 35L * 60L * 1000L) return "刚刚更新";
        if (age < STALE_AFTER_MS) return "更新于 " + timeText(state.summaryUpdatedAt);
        if (age < EXPIRED_AFTER_MS) return "数据可能已变化 · " + timeText(state.summaryUpdatedAt);
        return "打开 App 刷新";
    }

    private static String formatElapsed(long timestamp, long now, boolean compact) {
        if (timestamp <= 0L) return "暂无记录";
        long minutes = Math.max(0L, (now - timestamp) / 60_000L);
        if (minutes < 60L) return minutes + "分钟";
        if (minutes < 24L * 60L) {
            long hours = minutes / 60L;
            long remainder = minutes % 60L;
            if (remainder == 0L) return hours + (compact ? "时" : "小时");
            return hours + (compact ? "时" : "小时") + remainder + "分";
        }
        long days = minutes / (24L * 60L);
        long hours = (minutes % (24L * 60L)) / 60L;
        return days + "天" + (hours == 0L ? "" : hours + "小时");
    }

    private static String expectedFeedingText(long timestamp, long now, boolean compact) {
        if (timestamp <= 0L) return "暂无预计时间";
        boolean upcoming = timestamp >= now;
        long minutes = Math.abs(timestamp - now) / 60_000L;
        String duration = formatDuration(minutes, compact);
        return upcoming ? "预计还有 " + duration : "已超时 " + duration;
    }

    private static String formatDuration(long minutes, boolean compact) {
        if (minutes < 60L) return minutes + "分";
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        return hours + (compact ? "时" : "小时") + (remainder == 0L ? "" : remainder + "分");
    }

    private static String compactElapsed(long timestamp, long now) {
        if (timestamp <= 0L) return "—";
        long minutes = Math.max(0L, (now - timestamp) / 60_000L);
        if (minutes < 60L) return minutes + "分";
        if (minutes < 24L * 60L) {
            long hours = minutes / 60L;
            long remainder = minutes % 60L;
            return hours + "时" + (remainder == 0L ? "" : remainder + "分");
        }
        return minutes / (24L * 60L) + "天";
    }

    private static String diaperElapsed(long timestamp, long now, int variant) {
        if (variant != VARIANT_TALL) return compactElapsed(timestamp, now);
        if (timestamp <= 0L) return "—";
        long minutes = Math.max(0L, (now - timestamp) / 60_000L);
        if (minutes < 60L) return minutes + "分";
        if (minutes < 24L * 60L) {
            long hours = minutes / 60L;
            long remainder = minutes % 60L;
            return hours + ":" + (remainder < 10L ? "0" : "") + remainder;
        }
        return minutes / (24L * 60L) + "天";
    }

    private static String amount(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.format(Locale.US, "%.1f", value);
    }

    private static String sideLabel(String breastSide) {
        if ("LEFT".equals(breastSide)) return "左侧亲喂中";
        if ("RIGHT".equals(breastSide)) return "右侧亲喂中";
        return "双侧亲喂中";
    }

    private static String timeText(long timestamp) {
        DateFormat formatter = new SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE);
        return formatter.format(new Date(timestamp));
    }

    private static int resource(Context context, String type, String name) {
        int value = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (value == 0) throw new IllegalStateException("Missing widget resource: " + type + "/" + name);
        return value;
    }

    private static int id(Context context, String name) {
        return resource(context, "id", name);
    }
}
