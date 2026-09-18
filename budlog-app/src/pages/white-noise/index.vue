<script setup lang="ts">
import {
  ArrowLeft,
  AudioLines,
  Check,
  ChevronRight,
  Clock3,
  Info,
  Infinity as InfinityIcon,
  Moon,
  Pause,
  Play,
  RefreshCw,
  Repeat2,
  Search,
  SkipBack,
  SkipForward,
  SlidersHorizontal,
  Volume1,
  Volume2,
  X,
} from "lucide-vue-next";
import { NButton, NInput, NInputNumber } from "naive-ui";
import { onShow } from "@dcloudio/uni-app";
import { storeToRefs } from "pinia";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import { useWhiteNoisePlayerStore } from "../../stores/whiteNoisePlayer";
import { ensureAccess } from "../../utils/guard";

type TimerChoice = number | "custom" | null;

const player = useWhiteNoisePlayerStore();
const {
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
  currentTrack,
  canPrevious,
  canNext,
} = storeToRefs(player);

const loading = ref(false);
const error = ref("");
const search = ref("");
const timerOpen = ref(false);
const timerChoice = ref<TimerChoice>(null);
const customMinutes = ref<number | null>(30);
const timerPresets = [15, 30, 60, 90];

const selectedPlaylist = computed(() =>
  playlists.value.find((item) => item.id === selectedPlaylistId.value) || playlists.value[0],
);
const visibleTracks = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase();
  const tracks = selectedPlaylist.value?.tracks || [];
  return keyword ? tracks.filter((item) => item.name.toLocaleLowerCase().includes(keyword)) : tracks;
});
const totalTracks = computed(() => selectedPlaylist.value?.tracks.length || 0);
const timerSummary = computed(() => {
  if (sleepDeadline.value === null) return "不定时";
  return `剩余 ${formatClock(sleepRemainingSeconds.value)}`;
});

onShow(async () => {
  if (!(await ensureAccess())) return;
  player.initialize();
  await loadPlaylists();
});

async function loadPlaylists() {
  loading.value = true;
  error.value = "";
  try {
    player.setPlaylists(await api.whiteNoisePlaylists());
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "白噪音列表加载失败";
  } finally {
    loading.value = false;
  }
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.redirectTo({ url: "/pages/home/index" });
}

function choosePlaylist(id: string) {
  search.value = "";
  player.selectPlaylist(id);
}

function seek(event: { detail: { value: number } }) {
  player.seek(Number(event.detail.value));
}

function changeVolume(event: { detail: { value: number } }) {
  player.setVolume(Number(event.detail.value) / 100);
}

function openTimer() {
  const minutes = sleepDeadline.value === null
    ? null
    : Math.max(1, Math.ceil(sleepRemainingSeconds.value / 60));
  if (minutes !== null && timerPresets.includes(minutes)) {
    timerChoice.value = minutes;
  } else if (minutes !== null) {
    timerChoice.value = "custom";
    customMinutes.value = minutes;
  } else {
    timerChoice.value = null;
  }
  timerOpen.value = true;
}

function confirmTimer() {
  const minutes = timerChoice.value === "custom" ? Number(customMinutes.value) : timerChoice.value;
  if (minutes !== null && (!Number.isInteger(minutes) || minutes < 1 || minutes > 720)) {
    uni.showToast({ title: "请输入 1 到 720 分钟", icon: "none" });
    return;
  }
  try {
    player.setSleepTimer(minutes);
    timerOpen.value = false;
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "定时设置失败", icon: "none" });
  }
}

function formatClock(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "00:00";
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3_600);
  const minutes = Math.floor((total % 3_600) / 60);
  const remainder = total % 60;
  return hours > 0
    ? `${pad(hours)}:${pad(minutes)}:${pad(remainder)}`
    : `${pad(minutes)}:${pad(remainder)}`;
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}

function formatBytes(value: number) {
  if (value < 1_024 * 1_024) return `${Math.max(1, Math.round(value / 1_024))} KB`;
  return `${(value / 1_024 / 1_024).toFixed(1)} MB`;
}
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form white-noise-page">
      <view class="white-noise-head">
        <button class="icon-btn white-noise-head__back" aria-label="返回" title="返回" @click="goBack">
          <ArrowLeft :size="22" />
        </button>
        <view>
          <text class="white-noise-head__title">白噪音</text>
          <text class="white-noise-head__copy">给日常添一点安静</text>
        </view>
        <view class="white-noise-head__spacer" />
      </view>

      <view class="player-panel surface">
        <view class="player-panel__hero">
          <image class="player-panel__art" src="/static/ui/white-noise-hero.png" mode="widthFix" />
        </view>
        <view class="player-panel__body">
          <text class="player-panel__title">{{ currentTrack?.name || "选择一段白噪音" }}</text>
          <view class="player-panel__status" :class="{ 'player-panel__status--active': playing }">
            <AudioLines :size="15" />
            <text>{{ playing ? "正在播放" : currentTrack ? "已暂停" : "等待播放" }}</text>
          </view>

          <slider
            class="progress-slider"
            :value="currentTime"
            :max="Math.max(duration, 1)"
            :step="1"
            :disabled="!currentTrack || duration <= 0"
            active-color="#ff4f87"
            background-color="#e9eef5"
            block-color="#ff4f87"
            :block-size="18"
            @change="seek"
          />
          <view class="player-panel__times">
            <text>{{ formatClock(currentTime) }}</text>
            <text>{{ formatClock(duration) }}</text>
          </view>

          <view class="player-controls">
            <button
              class="player-controls__minor"
              :class="{ 'player-controls__minor--active': singleLoop }"
              :aria-label="singleLoop ? '关闭单曲循环' : '开启单曲循环'"
              :title="singleLoop ? '关闭单曲循环' : '开启单曲循环'"
              @click="player.toggleLoop"
            >
              <Repeat2 :size="22" /><text>单曲循环</text>
            </button>
            <button class="player-controls__skip" :disabled="!canPrevious" aria-label="上一首" title="上一首" @click="player.playPrevious">
              <SkipBack :size="25" fill="currentColor" />
            </button>
            <button
              class="player-controls__primary"
              :disabled="!currentTrack && !playlists.length"
              :aria-label="playing ? '暂停' : '播放'"
              :title="playing ? '暂停' : '播放'"
              @click="playing ? player.pausePlayback() : player.resumePlayback()"
            >
              <Pause v-if="playing" :size="31" fill="currentColor" />
              <Play v-else :size="31" fill="currentColor" />
            </button>
            <button class="player-controls__skip" :disabled="!canNext" aria-label="下一首" title="下一首" @click="player.playNext">
              <SkipForward :size="25" fill="currentColor" />
            </button>
            <button class="player-controls__minor" aria-label="设置定时关闭" title="设置定时关闭" @click="openTimer">
              <Clock3 :size="22" /><text>定时</text>
            </button>
          </view>

          <view v-if="volumeSupported" class="volume-control">
            <Volume1 :size="18" fill="currentColor" />
            <slider
              class="volume-control__slider"
              :value="Math.round(volume * 100)"
              :max="100"
              :step="1"
              active-color="#ff4f87"
              background-color="#e9eef5"
              block-color="#ffffff"
              :block-size="17"
              @changing="changeVolume"
              @change="changeVolume"
            />
            <Volume2 :size="18" fill="currentColor" />
          </view>
          <view v-else class="device-volume-hint"><Info :size="15" /><text>请使用设备音量键调节音量</text></view>

          <button class="timer-summary" :class="{ 'timer-summary--active': sleepDeadline !== null }" @click="openTimer">
            <Moon :size="19" fill="currentColor" />
            <text>{{ sleepDeadline === null ? "定时关闭" : "定时关闭 · " + timerSummary }}</text>
            <ChevronRight :size="18" />
          </button>
          <text v-if="playerError" class="player-error">{{ playerError }}</text>
        </view>
      </view>

      <view class="library-head">
        <text class="library-head__title">音频列表</text>
        <view class="library-head__actions">
          <text class="library-head__count">共 {{ totalTracks }} 首</text>
          <button class="icon-btn" :disabled="loading" aria-label="重新扫描音频目录" title="重新扫描" @click="loadPlaylists">
            <RefreshCw :size="20" :class="{ 'spin-icon': loading }" />
          </button>
        </view>
      </view>

      <AppLoading v-if="loading && !playlists.length" copy="正在扫描音频目录" />
      <ErrorState v-else-if="error && !playlists.length" title="音频目录暂时不可用" :copy="error" @retry="loadPlaylists" />
      <view v-else-if="!playlists.length" class="state-panel surface library-empty">
        <view class="state-panel__icon"><AudioLines :size="22" /></view>
        <text class="state-panel__title">还没有可播放的音频</text>
        <text class="state-panel__copy">目录中加入 MP3 文件后，点击重新扫描</text>
      </view>
      <template v-else>
        <scroll-view v-if="playlists.length > 1" class="playlist-tabs" scroll-x show-scrollbar="false">
          <view class="playlist-tabs__inner">
            <button
              v-for="playlist in playlists"
              :key="playlist.id"
              class="playlist-tab"
              :class="{ 'playlist-tab--active': playlist.id === selectedPlaylistId }"
              @click="choosePlaylist(playlist.id)"
            >
              {{ playlist.name }}
            </button>
          </view>
        </scroll-view>

        <NInput v-model:value="search" class="track-search" clearable placeholder="搜索音频名称">
          <template #prefix><Search :size="18" /></template>
        </NInput>
        <text v-if="error" class="library-warning">{{ error }}</text>

        <view v-if="visibleTracks.length" class="track-list surface">
          <button
            v-for="track in visibleTracks"
            :key="track.id"
            class="track-row"
            :class="{ 'track-row--active': track.id === currentTrackId }"
            @click="player.toggleTrack(track.id)"
          >
            <view class="track-row__icon"><AudioLines :size="21" /></view>
            <view class="track-row__body">
              <text class="track-row__name">{{ track.name }}</text>
              <text class="track-row__meta">{{ track.format }} · {{ formatBytes(track.sizeBytes) }}</text>
            </view>
            <view class="track-row__action">
              <AudioLines v-if="track.id === currentTrackId && playing" :size="18" />
              <Play v-else :size="17" fill="currentColor" />
            </view>
          </button>
        </view>
        <view v-else class="state-panel surface search-empty">
          <text class="state-panel__title">没有匹配的音频</text>
          <text class="state-panel__copy">换一个名称试试</text>
        </view>
      </template>

      <view v-if="timerOpen" class="timer-backdrop" @click.self="timerOpen = false">
        <view class="timer-sheet">
          <view class="timer-sheet__handle" />
          <view class="timer-sheet__head">
            <view class="timer-sheet__spacer" />
            <view><text class="timer-sheet__title">定时关闭</text><text class="timer-sheet__copy">到时暂停播放</text></view>
            <button class="icon-btn timer-sheet__close" aria-label="关闭" title="关闭" @click="timerOpen = false"><X :size="21" /></button>
          </view>
          <view class="timer-sheet__remaining">
            <view><Moon :size="29" fill="currentColor" /></view>
            <text>{{ sleepDeadline === null ? "--:--" : formatClock(sleepRemainingSeconds) }}</text>
            <small>{{ sleepDeadline === null ? "当前未设置" : "当前剩余时间" }}</small>
          </view>

          <view class="timer-grid">
            <button
              v-for="minutes in timerPresets"
              :key="minutes"
              class="timer-option"
              :class="{ 'timer-option--active': timerChoice === minutes }"
              @click="timerChoice = minutes"
            >
              <Check v-if="timerChoice === minutes" class="timer-option__check" :size="14" />
              <Clock3 :size="21" /><text>{{ minutes }} 分钟</text>
            </button>
            <button class="timer-option" :class="{ 'timer-option--active': timerChoice === 'custom' }" @click="timerChoice = 'custom'">
              <Check v-if="timerChoice === 'custom'" class="timer-option__check" :size="14" />
              <SlidersHorizontal :size="21" /><text>自定义</text>
            </button>
            <button class="timer-option" :class="{ 'timer-option--active': timerChoice === null }" @click="timerChoice = null">
              <Check v-if="timerChoice === null" class="timer-option__check" :size="14" />
              <InfinityIcon :size="23" /><text>不定时</text>
            </button>
          </view>

          <view v-if="timerChoice === 'custom'" class="custom-timer">
            <text>自定义分钟数</text>
            <NInputNumber v-model:value="customMinutes" :min="1" :max="720" :precision="0" button-placement="both" />
          </view>
          <NButton class="timer-confirm" type="primary" size="large" block @click="confirmTimer">确认设置</NButton>
          <view class="timer-sheet__hint"><Info :size="14" /><text>锁屏或后台运行时，暂停时间可能受系统限制</text></view>
        </view>
      </view>
    </view>
  </AppPage>
</template>

<style scoped>
.white-noise-page { padding-top: max(10px, env(safe-area-inset-top)); }
.white-noise-head { display: grid; min-height: 72px; grid-template-columns: 46px minmax(0, 1fr) 46px; align-items: center; margin-bottom: 10px; text-align: center; }
.white-noise-head__back { width: 44px; height: 44px; border: 1px solid rgba(233, 238, 245, 0.86); border-radius: 50%; background: rgba(255, 255, 255, 0.94); box-shadow: 0 7px 20px rgba(49, 70, 109, 0.1); }
.white-noise-head__title, .white-noise-head__copy { display: block; }
.white-noise-head__title { font-size: 21px; line-height: 28px; font-weight: 850; }
.white-noise-head__copy { margin-top: 1px; color: #8290aa; font-size: 12px; line-height: 18px; }
.player-panel { overflow: hidden; border-color: rgba(255, 255, 255, 0.92); background: #fff; box-shadow: 0 12px 32px rgba(70, 92, 131, 0.11); text-align: center; }
.player-panel__hero { width: 100%; overflow: hidden; background: #fff5f9; }
.player-panel__art { display: block; width: 100%; height: auto; }
.player-panel__body { padding: 5px 14px 14px; }
.player-panel__title { display: block; max-width: 92%; margin: 0 auto; overflow: hidden; color: var(--bud-color-ink); font-size: 18px; line-height: 27px; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.player-panel__status { display: inline-flex; min-height: 26px; align-items: center; gap: 5px; color: #8e9ab0; font-size: 11px; font-weight: 750; }
.player-panel__status--active { color: var(--bud-color-primary); }
.progress-slider { margin: 6px 7px -4px; }
.progress-slider:deep(.uni-slider-handle-wrapper),
.progress-slider:deep(.uni-slider-track) { height: 5px; border-radius: 999px; }
.progress-slider:deep(.uni-slider-thumb) { box-sizing: border-box; border: 3px solid #fff; box-shadow: 0 2px 7px rgba(63, 72, 94, 0.24); }
.player-panel__times { display: flex; justify-content: space-between; padding: 0 7px; color: #73809a; font-size: 10px; font-variant-numeric: tabular-nums; }
.player-controls { display: grid; grid-template-columns: 58px 40px 64px 40px 58px; align-items: center; justify-content: space-between; gap: 2px; margin-top: 5px; }
.player-controls button { margin: 0; padding: 0; border: 0; }
.player-controls__minor { display: flex; height: 56px; flex-direction: column; align-items: center; justify-content: center; gap: 3px; color: #77849c; background: transparent; font-size: 10px; line-height: 14px; white-space: nowrap; }
.player-controls__minor--active { color: var(--bud-color-primary); }
.player-controls__skip { display: flex; width: 40px; height: 46px; align-items: center; justify-content: center; border-radius: 50%; color: var(--bud-color-ink); background: transparent; }
.player-controls__primary { display: flex; width: 64px; height: 64px; align-items: center; justify-content: center; border-radius: 50%; color: #fff; background: var(--bud-color-primary); box-shadow: 0 10px 24px rgba(255, 79, 135, 0.32); }
.player-controls__primary .lucide-play { margin-left: 3px; }
.player-controls button[disabled] { opacity: 0.28; }
.volume-control { display: grid; min-height: 37px; grid-template-columns: 22px minmax(0, 1fr) 22px; align-items: center; gap: 5px; margin: 5px 1px 0; color: #71809a; }
.volume-control__slider { margin: 0 8px; }
.volume-control__slider:deep(.uni-slider-handle-wrapper),
.volume-control__slider:deep(.uni-slider-track) { height: 5px; border-radius: 999px; }
.volume-control__slider:deep(.uni-slider-thumb) { box-sizing: border-box; width: 18px !important; height: 18px !important; margin-top: -9px !important; margin-left: -9px !important; border: 1px solid #dfe5ee; box-shadow: 0 2px 7px rgba(63, 72, 94, 0.2); }
.device-volume-hint { display: flex; min-height: 35px; align-items: center; justify-content: center; gap: 5px; margin-top: 5px; color: var(--bud-color-muted); font-size: 10px; }
.timer-summary { display: grid; width: 100%; min-height: 48px; grid-template-columns: 25px minmax(0, 1fr) 20px; align-items: center; gap: 8px; margin: 8px 0 0; padding: 0 12px; border: 0; border-radius: 8px; color: #6b7891; background: #f6f8fc; text-align: left; font-size: 12px; font-weight: 780; }
.timer-summary--active { color: var(--bud-color-primary); background: #fff0f5; }
.timer-summary .lucide:last-child { justify-self: end; }
.player-error, .library-warning { display: block; margin-top: 8px; color: #a14440; font-size: 11px; line-height: 17px; }
.library-head { display: flex; min-height: 60px; align-items: center; justify-content: space-between; margin-top: 11px; padding: 0 2px; }
.library-head__actions { display: flex; align-items: center; gap: 4px; }
.library-head__title { font-size: 19px; line-height: 27px; font-weight: 850; }
.library-head__count { color: #8490a7; font-size: 11px; }
.library-head__actions .icon-btn { width: 40px; height: 40px; color: #66748d; }
.spin-icon { animation: spin 0.8s linear infinite; }
.playlist-tabs { width: 100%; margin-bottom: 10px; white-space: nowrap; }
.playlist-tabs__inner { display: inline-flex; min-width: 100%; gap: 8px; padding: 1px; }
.playlist-tab { min-height: 36px; margin: 0; padding: 0 14px; border: 1px solid var(--bud-color-line); border-radius: 8px; color: var(--bud-color-body); background: var(--bud-color-surface); font-size: 11px; font-weight: 750; }
.playlist-tab--active { border-color: #ff8aaf; color: var(--bud-color-primary-dark); background: var(--bud-color-primary-soft); }
.track-search { margin-bottom: 11px; }
.track-search:deep(.n-input-wrapper) { min-height: 45px; padding-left: 14px; padding-right: 12px; }
.track-search:deep(.n-input__placeholder) { color: #9ca6b9; }
.track-list { overflow: hidden; box-shadow: 0 8px 24px rgba(49, 70, 109, 0.07); }
.track-row { display: grid; width: 100%; min-height: 66px; grid-template-columns: 44px minmax(0, 1fr) 34px; align-items: center; gap: 10px; margin: 0; padding: 8px 12px; border: 0; border-bottom: 1px solid var(--bud-color-line-soft); background: transparent; text-align: left; }
.track-row:last-child { border-bottom: 0; }
.track-row--active { background: #fff0f5; }
.track-row__icon { display: flex; width: 44px; height: 44px; align-items: center; justify-content: center; border-radius: 8px; color: var(--bud-color-primary); background: #ffe4ed; }
.track-row:nth-child(4n + 2) .track-row__icon { color: var(--baby-blue); background: var(--baby-blue-soft); }
.track-row:nth-child(4n + 3) .track-row__icon { color: var(--baby-green); background: var(--baby-green-soft); }
.track-row:nth-child(4n) .track-row__icon { color: var(--baby-purple); background: var(--baby-purple-soft); }
.track-row__body { min-width: 0; }
.track-row__name, .track-row__meta { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.track-row__name { color: var(--bud-color-ink); font-size: 13px; line-height: 20px; font-weight: 800; }
.track-row__meta { margin-top: 2px; color: #929db2; font-size: 10px; line-height: 15px; }
.track-row__action { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; border: 1px solid #aeb9ca; border-radius: 50%; color: #68758d; }
.track-row--active .track-row__action { border-color: transparent; color: var(--bud-color-primary); }
.library-empty, .search-empty { min-height: 180px; }
.timer-backdrop { position: fixed; z-index: 60; inset: 0; display: flex; align-items: flex-end; justify-content: center; background: rgba(22, 28, 46, 0.5); backdrop-filter: blur(3px); }
.timer-sheet { width: min(100%, 430px); max-height: 92dvh; overflow-y: auto; padding: 8px 16px calc(18px + env(safe-area-inset-bottom)); border-radius: 20px 20px 0 0; background: var(--bud-color-surface); box-shadow: 0 -16px 42px rgba(31, 37, 53, 0.2); }
.timer-sheet__handle { width: 46px; height: 5px; margin: 0 auto 10px; border-radius: 3px; background: #d8dee9; }
.timer-sheet__head { display: grid; grid-template-columns: 40px minmax(0, 1fr) 40px; align-items: center; text-align: center; }
.timer-sheet__title, .timer-sheet__copy { display: block; }
.timer-sheet__title { font-size: 21px; line-height: 29px; font-weight: 850; }
.timer-sheet__copy { margin-top: 2px; color: #8793a9; font-size: 11px; }
.timer-sheet__close { width: 38px; height: 38px; border-radius: 50%; color: #8793a9; background: #f3f5f9; }
.timer-sheet__remaining { display: flex; flex-direction: column; align-items: center; margin: 20px 0 18px; }
.timer-sheet__remaining > view { display: flex; width: 68px; height: 68px; align-items: center; justify-content: center; border-radius: 50%; color: var(--bud-color-primary); background: #ffe7ef; }
.timer-sheet__remaining > text { margin-top: 8px; color: var(--bud-color-ink); font-size: 39px; line-height: 44px; font-weight: 850; font-variant-numeric: tabular-nums; }
.timer-sheet__remaining small { margin-top: 2px; color: #8894aa; font-size: 11px; }
.timer-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.timer-option { position: relative; display: flex; min-width: 0; min-height: 88px; flex-direction: column; align-items: center; justify-content: center; gap: 7px; margin: 0; padding: 8px 3px; border: 1px solid #e0e6ef; border-radius: 8px; color: #6f7d96; background: #fff; font-size: 12px; font-weight: 760; }
.timer-option--active { border-color: var(--bud-color-primary); color: var(--bud-color-primary); background: #fff4f7; box-shadow: inset 0 0 0 1px rgba(255, 79, 135, 0.08); }
.timer-option__check { position: absolute; top: 6px; right: 6px; padding: 3px; border-radius: 50%; color: #fff; background: var(--bud-color-primary); }
.custom-timer { display: grid; grid-template-columns: minmax(0, 1fr) 160px; align-items: center; gap: 10px; margin: 13px 0; color: var(--bud-color-body); font-size: 12px; font-weight: 700; }
.timer-grid + .timer-confirm, .custom-timer + .timer-confirm { margin-top: 16px; }
.timer-confirm:deep(.n-button__content) { font-size: 15px; font-weight: 800; }
.timer-confirm { min-height: 54px; }
.timer-sheet__hint { display: flex; align-items: center; justify-content: center; gap: 5px; margin-top: 14px; color: var(--bud-color-muted); font-size: 9px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 350px) { .player-controls { grid-template-columns: 50px 34px 58px 34px 50px; } .player-controls__primary { width: 58px; height: 58px; } .timer-grid { gap: 6px; } .timer-option { min-height: 80px; } .custom-timer { grid-template-columns: 1fr; } }
</style>
