package dev.oumuv.budlog.nativebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BudlogMediaService extends Service {
    static final String ACTION_COMMAND = "dev.oumuv.budlog.nativebridge.COMMAND";
    static final String EXTRA_COMMAND = "command";

    private static final String ACTION_PLAY = "dev.oumuv.budlog.nativebridge.PLAY";
    private static final String ACTION_PAUSE = "dev.oumuv.budlog.nativebridge.PAUSE";
    private static final String ACTION_PREVIOUS = "dev.oumuv.budlog.nativebridge.PREVIOUS";
    private static final String ACTION_NEXT = "dev.oumuv.budlog.nativebridge.NEXT";
    private static final String ACTION_STOP = "dev.oumuv.budlog.nativebridge.STOP";
    private static final String CHANNEL_ID = "budlog_white_noise";
    private static final int NOTIFICATION_ID = 4902;
    private static final long POSITION_UPDATE_INTERVAL_MS = 1_000L;
    private static final Object SNAPSHOT_LOCK = new Object();

    private static volatile BudlogMediaService activeInstance;
    private static String lastSnapshot = "{\"status\":\"idle\",\"trackId\":\"\",\"currentTime\":0,\"duration\":0,\"loop\":true,\"sleepDeadline\":null,\"error\":\"\"}";

    private final List<Track> queue = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable positionTicker = new Runnable() {
        @Override
        public void run() {
            if (sleepDeadline != null && sleepDeadline <= System.currentTimeMillis()) {
                sleepDeadline = null;
                pausePlayback(true);
            }
            publishSnapshot();
            if (!"idle".equals(status)) {
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (BudlogMediaService.this.resumeOnFocusGain && BudlogMediaService.this.prepared) {
                BudlogMediaService.this.resumeOnFocusGain = false;
                startPreparedPlayer();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            BudlogMediaService.this.resumeOnFocusGain = "playing".equals(BudlogMediaService.this.status);
            pausePlayback(false);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            BudlogMediaService.this.resumeOnFocusGain = false;
            pausePlayback(true);
        }
    };

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pausePlayback(true);
            }
        }
    };

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaSession mediaSession;
    private NotificationManager notificationManager;
    private int currentIndex = -1;
    private boolean prepared;
    private boolean playWhenPrepared;
    private boolean foregroundStarted;
    private boolean singleLoop = true;
    private boolean resumeOnFocusGain;
    private Long sleepDeadline;
    private String status = "idle";
    private String error = "";

    public static String snapshot() {
        BudlogMediaService service = activeInstance;
        if (service != null) return service.buildSnapshot();
        synchronized (SNAPSHOT_LOCK) {
            return lastSnapshot;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ensureNotificationChannel(this);
        createMediaSession();
        createPlayer();
        registerNoisyReceiver();
        publishSnapshot();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_COMMAND.equals(action)) {
            processCommand(intent.getStringExtra(EXTRA_COMMAND));
        } else if (ACTION_PLAY.equals(action)) {
            ensureForeground();
            resumePlayback();
        } else if (ACTION_PAUSE.equals(action)) {
            pausePlayback(true);
        } else if (ACTION_PREVIOUS.equals(action)) {
            playPrevious();
        } else if (ACTION_NEXT.equals(action)) {
            playNext();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(positionTicker);
        try {
            unregisterReceiver(noisyReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered.
        }
        abandonAudioFocus();
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        status = "idle";
        publishSnapshot();
        if (activeInstance == this) activeInstance = null;
        super.onDestroy();
    }

    private void processCommand(String commandJson) {
        if (commandJson == null) return;
        try {
            JSONObject command = new JSONObject(commandJson);
            String action = command.getString("action");
            if ("play".equals(action)) {
                loadQueue(command);
            } else if ("resume".equals(action)) {
                ensureForeground();
                resumePlayback();
            } else if ("pause".equals(action)) {
                pausePlayback(true);
            } else if ("previous".equals(action)) {
                playPrevious();
            } else if ("next".equals(action)) {
                playNext();
            } else if ("seek".equals(action)) {
                seekTo(command.getDouble("position"));
            } else if ("setLoop".equals(action)) {
                singleLoop = command.getBoolean("loop");
                updateSystemState();
            } else if ("setSleepTimer".equals(action)) {
                sleepDeadline = command.isNull("deadline") ? null : command.getLong("deadline");
                scheduleTicker();
                updateSystemState();
            } else if ("stop".equals(action)) {
                stopPlayback();
            }
        } catch (Exception exception) {
            setError("播放器命令处理失败");
        }
    }

    private void loadQueue(JSONObject command) throws JSONException {
        JSONArray items = command.getJSONArray("queue");
        List<Track> nextQueue = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            nextQueue.add(new Track(
                    item.getString("id"),
                    item.getString("title"),
                    item.getString("playlist"),
                    item.getString("url")
            ));
        }
        int nextIndex = command.getInt("index");
        if (nextQueue.isEmpty() || nextIndex < 0 || nextIndex >= nextQueue.size()) {
            throw new JSONException("Invalid media queue.");
        }
        queue.clear();
        queue.addAll(nextQueue);
        currentIndex = nextIndex;
        singleLoop = command.optBoolean("loop", true);
        error = "";
        ensureForeground();
        prepareCurrent(true);
    }

    private void createPlayer() {
        player = new MediaPlayer();
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        player.setOnPreparedListener(mediaPlayer -> {
            prepared = true;
            error = "";
            status = "paused";
            updateMetadata();
            if (playWhenPrepared) startPreparedPlayer();
            else updateSystemState();
        });
        player.setOnCompletionListener(mediaPlayer -> handleCompletion());
        player.setOnErrorListener((mediaPlayer, what, extra) -> {
            prepared = false;
            playWhenPrepared = false;
            setError("音频加载失败，请重新扫描后再试");
            return true;
        });
    }

    private void prepareCurrent(boolean autoPlay) {
        Track track = currentTrack();
        if (track == null || player == null) {
            stopPlayback();
            return;
        }
        try {
            prepared = false;
            playWhenPrepared = autoPlay;
            status = "loading";
            error = "";
            player.reset();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            Map<String, String> headers = new HashMap<>();
            String cookies = CookieManager.getInstance().getCookie(track.url);
            if (cookies != null && cookies.length() > 0) headers.put("Cookie", cookies);
            player.setDataSource(this, Uri.parse(track.url), headers);
            player.prepareAsync();
            updateMetadata();
            updateSystemState();
            scheduleTicker();
        } catch (Exception exception) {
            prepared = false;
            playWhenPrepared = false;
            setError("音频加载失败，请重新扫描后再试");
        }
    }

    private void resumePlayback() {
        if (currentTrack() == null) {
            if (foregroundStarted) {
                stopForeground(true);
                foregroundStarted = false;
            }
            stopSelf();
            return;
        }
        if (!prepared) {
            playWhenPrepared = true;
            if (!"loading".equals(status)) prepareCurrent(true);
            return;
        }
        startPreparedPlayer();
    }

    private void startPreparedPlayer() {
        if (player == null || !prepared) return;
        if (!requestAudioFocus()) {
            status = "paused";
            updateSystemState();
            return;
        }
        try {
            player.start();
            playWhenPrepared = false;
            status = "playing";
            error = "";
            ensureForeground();
            updateSystemState();
            scheduleTicker();
        } catch (IllegalStateException exception) {
            setError("音频播放失败，请稍后重试");
        }
    }

    private void pausePlayback(boolean abandonFocus) {
        playWhenPrepared = false;
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) player.pause();
            } catch (IllegalStateException ignored) {
                // State is reconciled below.
            }
        }
        if (currentTrack() != null && !"error".equals(status)) status = "paused";
        if (abandonFocus) abandonAudioFocus();
        updateSystemState();
    }

    private void playPrevious() {
        if (currentIndex <= 0) return;
        currentIndex--;
        ensureForeground();
        prepareCurrent(true);
    }

    private void playNext() {
        if (currentIndex < 0 || currentIndex >= queue.size() - 1) return;
        currentIndex++;
        ensureForeground();
        prepareCurrent(true);
    }

    private void seekTo(double seconds) {
        if (player == null || !prepared) return;
        int duration = safeDuration();
        int position = (int) Math.min(Math.max(0, seconds * 1000), duration);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
        } else {
            player.seekTo(position);
        }
        updateSystemState();
    }

    private void handleCompletion() {
        if (singleLoop) {
            if (player != null) {
                player.seekTo(0);
                startPreparedPlayer();
            }
        } else if (currentIndex >= 0 && currentIndex < queue.size() - 1) {
            currentIndex++;
            prepareCurrent(true);
        } else {
            status = "paused";
            updateSystemState();
        }
    }

    private void stopPlayback() {
        handler.removeCallbacks(positionTicker);
        sleepDeadline = null;
        resumeOnFocusGain = false;
        playWhenPrepared = false;
        prepared = false;
        abandonAudioFocus();
        if (player != null) {
            try {
                player.reset();
            } catch (IllegalStateException ignored) {
                // Player is about to be discarded with the service.
            }
        }
        queue.clear();
        currentIndex = -1;
        status = "idle";
        error = "";
        if (mediaSession != null) mediaSession.setActive(false);
        publishSnapshot();
        if (foregroundStarted) {
            stopForeground(true);
            foregroundStarted = false;
        }
        stopSelf();
    }

    private void setError(String message) {
        error = message;
        status = "error";
        abandonAudioFocus();
        updateSystemState();
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "BudlogWhiteNoise");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                ensureForeground();
                resumePlayback();
            }

            @Override
            public void onPause() {
                pausePlayback(true);
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSeekTo(long positionMs) {
                seekTo(positionMs / 1000.0);
            }

            @Override
            public void onStop() {
                stopPlayback();
            }
        });
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mediaSession.setSessionActivity(PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
            ));
        }
    }

    private void updateMetadata() {
        Track track = currentTrack();
        if (mediaSession == null || track == null) return;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.playlist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Budlog 白噪音")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration())
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void updateSystemState() {
        updateMediaSessionState();
        publishSnapshot();
        if (currentTrack() != null && notificationManager != null) {
            Notification notification = buildNotification();
            if (foregroundStarted) notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        if (currentIndex > 0) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (currentIndex >= 0 && currentIndex < queue.size() - 1) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        int playbackState;
        if ("playing".equals(status)) playbackState = PlaybackState.STATE_PLAYING;
        else if ("loading".equals(status)) playbackState = PlaybackState.STATE_BUFFERING;
        else if ("error".equals(status)) playbackState = PlaybackState.STATE_ERROR;
        else if ("idle".equals(status)) playbackState = PlaybackState.STATE_NONE;
        else playbackState = PlaybackState.STATE_PAUSED;
        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, safePosition(), 1f);
        if ("error".equals(status)) builder.setErrorMessage(error);
        mediaSession.setPlaybackState(builder.build());
        mediaSession.setActive(currentTrack() != null);
    }

    private Notification buildNotification() {
        Track track = currentTrack();
        String title = track == null ? "Budlog 白噪音" : track.title;
        String subtitle = track == null ? "准备播放" : track.playlist;
        Notification.Action previous = new Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "上一首",
                servicePendingIntent(ACTION_PREVIOUS, 1)
        ).build();
        boolean isPlaying = "playing".equals(status) || "loading".equals(status);
        Notification.Action playPause = new Notification.Action.Builder(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "暂停" : "播放",
                servicePendingIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY, 2)
        ).build();
        Notification.Action next = new Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "下一首",
                servicePendingIntent(ACTION_NEXT, 3)
        ).build();

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        int smallIcon = getResources().getIdentifier("ic_budlog_wave", "drawable", getPackageName());
        builder.setSmallIcon(smallIcon == 0 ? android.R.drawable.ic_media_play : smallIcon)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setShowWhen(false)
                .addAction(previous)
                .addAction(playPause)
                .addAction(next)
                .setDeleteIntent(servicePendingIntent(ACTION_STOP, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            builder.setContentIntent(PendingIntent.getActivity(
                    this,
                    5,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
            ));
        }
        return builder.build();
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, BudlogMediaService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void ensureForeground() {
        if (foregroundStarted) return;
        startForeground(NOTIFICATION_ID, buildNotification());
        foregroundStarted = true;
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context == null) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "白噪音播放",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("显示 Budlog 白噪音的播放状态和控制按钮");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build())
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(focusChangeListener);
        }
    }

    private void registerNoisyReceiver() {
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, filter);
        }
    }

    private void scheduleTicker() {
        handler.removeCallbacks(positionTicker);
        if (!"idle".equals(status)) handler.post(positionTicker);
    }

    private Track currentTrack() {
        return currentIndex >= 0 && currentIndex < queue.size() ? queue.get(currentIndex) : null;
    }

    private int safePosition() {
        if (player == null || !prepared) return 0;
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException exception) {
            return 0;
        }
    }

    private int safeDuration() {
        if (player == null || !prepared) return 0;
        try {
            return Math.max(0, player.getDuration());
        } catch (IllegalStateException exception) {
            return 0;
        }
    }

    private String buildSnapshot() {
        JSONObject snapshot = new JSONObject();
        Track track = currentTrack();
        try {
            snapshot.put("status", status);
            snapshot.put("trackId", track == null ? "" : track.id);
            snapshot.put("currentTime", safePosition() / 1000.0);
            snapshot.put("duration", safeDuration() / 1000.0);
            snapshot.put("loop", singleLoop);
            snapshot.put("sleepDeadline", sleepDeadline == null ? JSONObject.NULL : sleepDeadline);
            snapshot.put("error", error);
        } catch (JSONException ignored) {
            return lastSnapshot;
        }
        return snapshot.toString();
    }

    private void publishSnapshot() {
        String snapshot = buildSnapshot();
        synchronized (SNAPSHOT_LOCK) {
            lastSnapshot = snapshot;
        }
        updateMediaSessionState();
    }

    private static final class Track {
        private final String id;
        private final String title;
        private final String playlist;
        private final String url;

        private Track(String id, String title, String playlist, String url) {
            this.id = id;
            this.title = title;
            this.playlist = playlist;
            this.url = url;
        }
    }
}
