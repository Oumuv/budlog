package dev.oumuv.budlog.nativebridge;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.CookieManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BudlogWidgetRefreshJobService extends JobService {
    private static final int PERIODIC_JOB_ID = 9411;
    private static final int IMMEDIATE_JOB_ID = 9412;
    private static final long PERIODIC_INTERVAL_MS = 30L * 60L * 1000L;
    private static final int MAX_RESPONSE_CHARS = 128 * 1024;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private volatile Thread worker;

    static boolean scheduleImmediate(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (!hasWidgets(applicationContext)) return false;
        JobScheduler scheduler = (JobScheduler) applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;
        JobInfo job = new JobInfo.Builder(
                IMMEDIATE_JOB_ID,
                new ComponentName(applicationContext, BudlogWidgetRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    static void schedulePeriodicIfNeeded(Context context) {
        Context applicationContext = context.getApplicationContext();
        JobScheduler scheduler = (JobScheduler) applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (!hasWidgets(applicationContext)) {
            scheduler.cancel(PERIODIC_JOB_ID);
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(
                PERIODIC_JOB_ID,
                new ComponentName(applicationContext, BudlogWidgetRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPeriodic(PERIODIC_INTERVAL_MS, 5L * 60L * 1000L);
        } else {
            builder.setPeriodic(PERIODIC_INTERVAL_MS);
        }
        scheduler.schedule(builder.build());
    }

    static void cancelAll(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(PERIODIC_JOB_ID);
        scheduler.cancel(IMMEDIATE_JOB_ID);
    }

    private static boolean hasWidgets(Context context) {
        int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, BudlogWidgetProvider.class));
        return ids != null && ids.length > 0;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (!RUNNING.compareAndSet(false, true)) return false;
        final BudlogWidgetRepository repository = new BudlogWidgetRepository(this);
        if (repository.readState().authRequired) {
            RUNNING.set(false);
            return false;
        }

        repository.markRefreshStarted();
        BudlogWidgetRenderer.updateAll(this);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    refresh(repository);
                } finally {
                    RUNNING.set(false);
                    worker = null;
                    BudlogWidgetRenderer.updateAll(BudlogWidgetRefreshJobService.this);
                    jobFinished(params, false);
                }
            }
        }, "budlog-widget-refresh");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread activeWorker = worker;
        if (activeWorker != null) activeWorker.interrupt();
        return true;
    }

    private void refresh(BudlogWidgetRepository repository) {
        SharedPreferences bridgePreferences = getSharedPreferences(
                BudlogNativeIntegration.PREFS_NAME,
                Context.MODE_PRIVATE);
        String origin = bridgePreferences.getString(BudlogNativeIntegration.PREF_ALLOWED_ORIGIN, "");
        if (origin == null || origin.length() == 0) {
            repository.markNetworkFailure();
            return;
        }

        String endpoint = origin + "/api/v1/widget/summary";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            String cookies = CookieManager.getInstance().getCookie(endpoint);
            if (cookies != null && cookies.length() > 0) {
                connection.setRequestProperty("Cookie", cookies);
            }

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                repository.markAuthRequired();
                return;
            }
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                repository.markUnsupported();
                return;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                repository.markNetworkFailure();
                return;
            }

            String body = readBody(connection.getInputStream());
            JSONObject summary = validateAndExtractSummary(body);
            repository.saveSummary(summary, System.currentTimeMillis());
        } catch (IOException | JSONException | RuntimeException exception) {
            repository.markNetworkFailure();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBody(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (body.length() + count > MAX_RESPONSE_CHARS) {
                throw new IOException("Widget summary response is too large.");
            }
            body.append(buffer, 0, count);
        }
        return body.toString();
    }

    private JSONObject validateAndExtractSummary(String body) throws JSONException {
        JSONObject envelope = new JSONObject(body);
        if (envelope.getInt("code") != 0) throw new JSONException("Widget summary request failed.");
        JSONObject input = envelope.getJSONObject("data");

        String generatedAt = requiredTime(input, "generatedAt");
        boolean configured = input.getBoolean("configured");
        String babyName = nullableBoundedString(input, "babyName", 50);
        if (configured && babyName.length() == 0) throw new JSONException("Missing baby name.");

        JSONObject output = new JSONObject()
                .put("generatedAt", generatedAt)
                .put("configured", configured)
                .put("babyName", babyName.length() == 0 ? JSONObject.NULL : babyName);
        copyNullableTime(input, output, "lastFeedingTime");
        copyNullableTime(input, output, "nextExpectedFeedingTime");
        copyNullableTime(input, output, "lastPeeTime");
        copyNullableTime(input, output, "lastPoopTime");

        JSONObject inputToday = input.getJSONObject("today");
        JSONObject today = new JSONObject()
                .put("feedingCount", boundedInt(inputToday, "feedingCount", 10_000))
                .put("bottleAmountMl", boundedDouble(inputToday, "bottleAmountMl", 1_000_000D))
                .put("peeCount", boundedInt(inputToday, "peeCount", 10_000))
                .put("poopCount", boundedInt(inputToday, "poopCount", 10_000))
                .put("milkStorageCount", boundedInt(inputToday, "milkStorageCount", 10_000))
                .put("storedMilkAmountMl", boundedDouble(inputToday, "storedMilkAmountMl", 1_000_000D));
        return output.put("today", today);
    }

    private String requiredTime(JSONObject input, String key) throws JSONException {
        String value = input.getString(key);
        if (BudlogWidgetRepository.parseIsoMillis(value) < 0L) throw new JSONException("Invalid " + key + ".");
        return value;
    }

    private void copyNullableTime(JSONObject input, JSONObject output, String key) throws JSONException {
        if (input.isNull(key)) {
            output.put(key, JSONObject.NULL);
            return;
        }
        output.put(key, requiredTime(input, key));
    }

    private String nullableBoundedString(JSONObject input, String key, int maxLength) throws JSONException {
        if (input.isNull(key)) return "";
        String value = input.getString(key).trim();
        if (value.length() > maxLength) throw new JSONException("Invalid " + key + ".");
        return value;
    }

    private int boundedInt(JSONObject input, String key, int maxValue) throws JSONException {
        int value = input.getInt(key);
        if (value < 0 || value > maxValue) throw new JSONException("Invalid " + key + ".");
        return value;
    }

    private double boundedDouble(JSONObject input, String key, double maxValue) throws JSONException {
        double value = input.getDouble(key);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0D || value > maxValue) {
            throw new JSONException("Invalid " + key + ".");
        }
        return value;
    }
}
