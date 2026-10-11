package dev.oumuv.budlog.nativebridge;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class BudlogWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "dev.oumuv.budlog.nativebridge.action.REFRESH_WIDGET";

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        BudlogWidgetRefreshJobService.schedulePeriodicIfNeeded(context);
        BudlogWidgetRefreshJobService.scheduleImmediate(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            BudlogWidgetRenderer.update(context, manager, appWidgetId);
        }
        BudlogWidgetRefreshJobService.schedulePeriodicIfNeeded(context);
        BudlogWidgetRefreshJobService.scheduleImmediate(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            Bundle newOptions) {
        BudlogWidgetRenderer.update(context, manager, appWidgetId);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        BudlogWidgetRefreshJobService.cancelAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            BudlogWidgetRenderer.updateAll(context);
            BudlogWidgetRefreshJobService.scheduleImmediate(context);
            return;
        }
        super.onReceive(context, intent);
    }
}
