import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { WhiteNoisePlaylist, WhiteNoiseTrack } from "../types";

interface TrackContext {
  playlist: WhiteNoisePlaylist;
  track: WhiteNoiseTrack;
  index: number;
}

let audio: HTMLAudioElement | undefined;
let timerInterval: ReturnType<typeof setInterval> | undefined;
let lifecycleBound = false;

export const useWhiteNoisePlayerStore = defineStore("whiteNoisePlayer", () => {
  const playlists = ref<WhiteNoisePlaylist[]>([]);
  const selectedPlaylistId = ref("");
  const currentTrackId = ref("");
  const playing = ref(false);
  const currentTime = ref(0);
  const duration = ref(0);
  const singleLoop = ref(true);
  const volume = ref(1);
  const volumeSupported = ref(false);
  const playerError = ref("");
  const sleepDeadline = ref<number | null>(null);
  const sleepRemainingSeconds = ref(0);
  const mediaSessionSupported = ref(false);

  const currentContext = computed<TrackContext | undefined>(() => findTrack(currentTrackId.value));
  const currentTrack = computed(() => currentContext.value?.track);
  const currentPlaylist = computed(() => currentContext.value?.playlist);
  const canPrevious = computed(() => (currentContext.value?.index ?? 0) > 0);
  const canNext = computed(() => {
    const context = currentContext.value;
    return Boolean(context && context.index < context.playlist.tracks.length - 1);
  });

  function initialize() {
    ensureAudio();
    bindLifecycle();
    checkSleepTimer();
  }

  function ensureAudio(): HTMLAudioElement | undefined {
    if (audio || typeof Audio === "undefined") return audio;
    audio = new Audio();
    audio.preload = "metadata";
    audio.loop = singleLoop.value;
    audio.addEventListener("play", () => {
      playing.value = true;
      playerError.value = "";
      updateMediaSessionState();
    });
    audio.addEventListener("pause", () => {
      playing.value = false;
      updateMediaSessionState();
    });
    audio.addEventListener("loadedmetadata", syncAudioState);
    audio.addEventListener("durationchange", syncAudioState);
    audio.addEventListener("timeupdate", () => {
      syncAudioState();
      checkSleepTimer();
      updateMediaSessionPosition();
    });
    audio.addEventListener("volumechange", () => {
      if (audio) volume.value = audio.volume;
    });
    audio.addEventListener("ended", () => {
      if (singleLoop.value) return;
      if (canNext.value) void playNext();
      else playing.value = false;
    });
    audio.addEventListener("error", () => {
      if (!currentTrackId.value) return;
      playing.value = false;
      playerError.value = "音频加载失败，请重新扫描后再试";
      updateMediaSessionState();
    });
    detectVolumeSupport();
    setupMediaSession();
    return audio;
  }

  function setPlaylists(next: WhiteNoisePlaylist[]) {
    playlists.value = next;
    if (!next.some((item) => item.id === selectedPlaylistId.value)) {
      selectedPlaylistId.value = next[0]?.id || "";
    }
    if (currentTrackId.value && !findTrack(currentTrackId.value)) {
      disposeCurrentTrack();
    } else {
      updateMediaSessionMetadata();
      updateMediaSessionActions();
    }
  }

  function selectPlaylist(id: string) {
    if (playlists.value.some((item) => item.id === id)) selectedPlaylistId.value = id;
  }

  async function toggleTrack(trackId: string) {
    if (trackId === currentTrackId.value) {
      if (playing.value) pausePlayback();
      else await resumePlayback();
      return;
    }
    await playTrack(trackId);
  }

  async function playTrack(trackId: string) {
    const context = findTrack(trackId);
    const player = ensureAudio();
    if (!context || !player) {
      playerError.value = "当前浏览器无法创建音频播放器";
      return;
    }
    playerError.value = "";
    currentTrackId.value = context.track.id;
    selectedPlaylistId.value = context.playlist.id;
    currentTime.value = 0;
    duration.value = 0;
    player.loop = singleLoop.value;
    player.src = context.track.streamUrl;
    player.load();
    updateMediaSessionMetadata();
    updateMediaSessionActions();
    try {
      await player.play();
    } catch (exception) {
      playing.value = false;
      playerError.value = exception instanceof Error && exception.name === "NotAllowedError"
        ? "请点击播放按钮开始播放"
        : "音频播放失败，请稍后重试";
      updateMediaSessionState();
    }
  }

  async function resumePlayback() {
    const player = ensureAudio();
    if (!player) {
      playerError.value = "当前浏览器无法创建音频播放器";
      return;
    }
    if (!currentTrack.value) {
      const fallbackPlaylist = playlists.value.find((item) => item.id === selectedPlaylistId.value)
        || playlists.value[0];
      const fallback = fallbackPlaylist?.tracks[0];
      if (fallback) await playTrack(fallback.id);
      return;
    }
    try {
      await player.play();
    } catch (exception) {
      playing.value = false;
      playerError.value = exception instanceof Error && exception.name === "NotAllowedError"
        ? "请点击播放按钮开始播放"
        : "音频播放失败，请稍后重试";
    }
  }

  function pausePlayback() {
    audio?.pause();
  }

  async function playPrevious() {
    const context = currentContext.value;
    if (!context || context.index === 0) return;
    await playTrack(context.playlist.tracks[context.index - 1].id);
  }

  async function playNext() {
    const context = currentContext.value;
    if (!context || context.index >= context.playlist.tracks.length - 1) return;
    await playTrack(context.playlist.tracks[context.index + 1].id);
  }

  function seek(seconds: number) {
    if (!audio || !Number.isFinite(duration.value) || duration.value <= 0) return;
    audio.currentTime = Math.min(Math.max(0, seconds), duration.value);
    syncAudioState();
    updateMediaSessionPosition();
  }

  function toggleLoop() {
    singleLoop.value = !singleLoop.value;
    if (audio) audio.loop = singleLoop.value;
  }

  function setVolume(value: number) {
    if (!audio || !volumeSupported.value) return;
    const next = Math.min(1, Math.max(0, value));
    try {
      audio.volume = next;
      volume.value = audio.volume;
      if (Math.abs(audio.volume - next) > 0.01) volumeSupported.value = false;
    } catch {
      volumeSupported.value = false;
    }
  }

  function setSleepTimer(minutes: number | null) {
    if (minutes === null) {
      sleepDeadline.value = null;
      sleepRemainingSeconds.value = 0;
      stopTimerLoop();
      return;
    }
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 720) {
      throw new Error("定时时长应为 1 到 720 分钟");
    }
    sleepDeadline.value = Date.now() + minutes * 60_000;
    checkSleepTimer();
    startTimerLoop();
  }

  function handleResume() {
    syncAudioState();
    checkSleepTimer();
  }

  function disposePlayback() {
    setSleepTimer(null);
    disposeCurrentTrack();
    playlists.value = [];
    selectedPlaylistId.value = "";
  }

  function disposeCurrentTrack() {
    if (audio) {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
    }
    currentTrackId.value = "";
    currentTime.value = 0;
    duration.value = 0;
    playing.value = false;
    playerError.value = "";
    clearMediaSession();
  }

  function findTrack(trackId: string): TrackContext | undefined {
    if (!trackId) return undefined;
    for (const playlist of playlists.value) {
      const index = playlist.tracks.findIndex((item) => item.id === trackId);
      if (index >= 0) return { playlist, track: playlist.tracks[index], index };
    }
    return undefined;
  }

  function syncAudioState() {
    if (!audio) return;
    currentTime.value = Number.isFinite(audio.currentTime) ? audio.currentTime : 0;
    duration.value = Number.isFinite(audio.duration) ? audio.duration : 0;
    playing.value = !audio.paused && !audio.ended;
    volume.value = audio.volume;
    updateMediaSessionState();
  }

  function detectVolumeSupport() {
    if (!audio) return;
    try {
      const original = audio.volume;
      const probe = Math.abs(original - 0.47) < 0.01 ? 0.53 : 0.47;
      audio.volume = probe;
      volumeSupported.value = Math.abs(audio.volume - probe) < 0.01;
      audio.volume = original;
      volume.value = audio.volume;
    } catch {
      volumeSupported.value = false;
    }
  }

  function bindLifecycle() {
    if (lifecycleBound || typeof document === "undefined") return;
    lifecycleBound = true;
    document.addEventListener("visibilitychange", handleResume);
    window.addEventListener("pageshow", handleResume);
    window.addEventListener("focus", handleResume);
  }

  function startTimerLoop() {
    if (timerInterval) return;
    timerInterval = setInterval(checkSleepTimer, 1_000);
  }

  function stopTimerLoop() {
    if (!timerInterval) return;
    clearInterval(timerInterval);
    timerInterval = undefined;
  }

  function checkSleepTimer() {
    if (sleepDeadline.value === null) {
      sleepRemainingSeconds.value = 0;
      stopTimerLoop();
      return;
    }
    const remaining = Math.max(0, Math.ceil((sleepDeadline.value - Date.now()) / 1_000));
    sleepRemainingSeconds.value = remaining;
    if (remaining > 0) {
      startTimerLoop();
      return;
    }
    sleepDeadline.value = null;
    stopTimerLoop();
    pausePlayback();
  }

  function getMediaSession(): MediaSession | undefined {
    return typeof navigator !== "undefined" && "mediaSession" in navigator
      ? navigator.mediaSession
      : undefined;
  }

  function setupMediaSession() {
    const session = getMediaSession();
    mediaSessionSupported.value = Boolean(session);
    if (!session) return;
    try {
      session.setActionHandler("play", () => void resumePlayback());
      session.setActionHandler("pause", pausePlayback);
      updateMediaSessionActions();
    } catch {
      mediaSessionSupported.value = false;
    }
  }

  function updateMediaSessionActions() {
    const session = getMediaSession();
    if (!session || !mediaSessionSupported.value) return;
    try {
      session.setActionHandler("previoustrack", canPrevious.value ? () => void playPrevious() : null);
      session.setActionHandler("nexttrack", canNext.value ? () => void playNext() : null);
    } catch {
      // Some browsers expose Media Session but omit individual actions.
    }
  }

  function updateMediaSessionMetadata() {
    const session = getMediaSession();
    const context = currentContext.value;
    if (!session || !context || typeof MediaMetadata === "undefined") return;
    session.metadata = new MediaMetadata({
      title: context.track.name,
      artist: context.playlist.name,
      album: "Budlog 白噪音",
      artwork: [{ src: "/static/ui/baby-sleep.png", sizes: "154x92", type: "image/png" }],
    });
    updateMediaSessionState();
    updateMediaSessionPosition();
  }

  function updateMediaSessionState() {
    const session = getMediaSession();
    if (!session) return;
    try {
      session.playbackState = playing.value ? "playing" : currentTrackId.value ? "paused" : "none";
    } catch {
      // Playback state is optional on older implementations.
    }
  }

  function updateMediaSessionPosition() {
    const session = getMediaSession();
    if (!session || typeof session.setPositionState !== "function" || duration.value <= 0) return;
    try {
      session.setPositionState({
        duration: duration.value,
        playbackRate: audio?.playbackRate || 1,
        position: Math.min(currentTime.value, duration.value),
      });
    } catch {
      // Ignore transient metadata states while a new source is loading.
    }
  }

  function clearMediaSession() {
    const session = getMediaSession();
    if (!session) return;
    session.metadata = null;
    updateMediaSessionState();
    updateMediaSessionActions();
  }

  return {
    playlists,
    selectedPlaylistId,
    currentTrackId,
    playing,
    currentTime,
    duration,
    singleLoop,
    volume,
    volumeSupported,
    playerError,
    sleepDeadline,
    sleepRemainingSeconds,
    mediaSessionSupported,
    currentTrack,
    currentPlaylist,
    canPrevious,
    canNext,
    initialize,
    setPlaylists,
    selectPlaylist,
    toggleTrack,
    resumePlayback,
    pausePlayback,
    playPrevious,
    playNext,
    seek,
    toggleLoop,
    setVolume,
    setSleepTimer,
    handleResume,
    disposePlayback,
  };
});
