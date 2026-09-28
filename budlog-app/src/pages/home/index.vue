<script setup lang="ts">
import {
  AudioLines,
  Baby,
  BellRing,
  CalendarDays,
  Check,
  ChevronRight,
  Droplets,
  Milk,
  NotebookPen,
  PackagePlus,
  Scale,
  SquareCheckBig,
  Timer,
} from "lucide-vue-next";
import { NButton } from "naive-ui";
import { onHide, onShow } from "@dcloudio/uni-app";
import { computed, onUnmounted, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { Dashboard, TodoTask } from "../../types";
import { formatDate, formatDateTime, formatDuration, setAppTimezone, toLocalInput } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

const dashboard = ref<Dashboard>();
const loading = ref(true);
const error = ref("");
const now = ref(Date.now());
const dueReminders = ref<TodoTask[]>([]);
const reminderIndex = ref(0);
const completingReminderId = ref<number>();
let reminderRefreshPromise: Promise<void> | undefined;
let clock: ReturnType<typeof setInterval> | undefined;

const calendarAgeDays = computed(() => Math.max(1, dashboard.value?.baby?.ageDayNumber ?? 1));
const feedingMinutes = computed(() => elapsedMinutes(dashboard.value?.lastFeeding?.startTime));
const peeMinutes = computed(() => elapsedMinutes(dashboard.value?.lastPee?.recordTime));
const poopMinutes = computed(() => elapsedMinutes(dashboard.value?.lastPoop?.recordTime));
const nextFeedingText = computed(() => {
  const value = dashboard.value?.nextExpectedFeedingTime;
  if (!value) return "暂无预计时间";
  const minutes = Math.ceil((new Date(value).getTime() - now.value) / 60_000);
  return minutes >= 0 ? `还有 ${formatDuration(minutes)}` : `已超时 ${formatDuration(Math.abs(minutes))}`;
});
const highlightedMilestones = computed(() =>
  [...(dashboard.value?.milestones ?? [])]
    .filter((item) => item.daysDifference >= 0)
    .sort((a, b) => a.daysDifference - b.daysDifference)
    .slice(0, 2),
);

onShow(async () => {
  if (!(await ensureAccess())) return;
  await load();
  now.value = Date.now();
  if (clock) clearInterval(clock);
  clock = setInterval(() => {
    now.value = Date.now();
    void refreshDueReminders();
  }, 60_000);
});

onHide(stopClock);
onUnmounted(stopClock);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    dashboard.value = await api.dashboard();
    if (dashboard.value.baby) setAppTimezone(dashboard.value.baby.timezone);
    await refreshDueReminders();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "首页加载失败";
  } finally {
    loading.value = false;
  }
}

async function refreshDueReminders(force = false) {
  if (!dashboard.value?.configured) {
    dueReminders.value = [];
    return;
  }
  if (reminderRefreshPromise) {
    await reminderRefreshPromise;
    if (!force) return;
  }
  const refresh = api.dueReminders()
    .then((tasks) => {
      dueReminders.value = tasks;
      if (reminderIndex.value >= dueReminders.value.length) reminderIndex.value = 0;
    })
    .catch((exception) => console.warn("首页提醒刷新失败", exception));
  reminderRefreshPromise = refresh;
  await refresh;
  if (reminderRefreshPromise === refresh) reminderRefreshPromise = undefined;
}

async function completeReminder(task: TodoTask) {
  if (completingReminderId.value) return;
  completingReminderId.value = task.id;
  try {
    await api.updateTaskStatus(task.id, "DONE");
    clearShownReminder(task.id);
    dueReminders.value = dueReminders.value.filter((item) => item.id !== task.id);
    if (dashboard.value) dashboard.value.tasks = dashboard.value.tasks.filter((item) => item.id !== task.id);
    if (reminderIndex.value >= dueReminders.value.length) reminderIndex.value = 0;
    await refreshDueReminders(true);
    uni.showToast({ title: "已完成", icon: "success" });
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "任务更新失败", icon: "none" });
  } finally {
    completingReminderId.value = undefined;
  }
}

function onReminderChange(event: Event) {
  const current = (event as Event & { detail?: { current?: number } }).detail?.current;
  if (typeof current === "number") reminderIndex.value = current;
}

function reminderTiming(task: TodoTask): string {
  const state = task.overdue ? "已逾期" : "提醒已生效";
  return `${state} · 到期 ${formatDateTime(task.dueTime)}`;
}

function go(url: string) {
  uni.navigateTo({ url });
}

function redirect(url: string) {
  uni.redirectTo({ url });
}

function milestoneLabel(days: number) {
  if (days === 0) return "今天";
  return days > 0 ? `还有 ${days} 天` : `已过 ${Math.abs(days)} 天`;
}

function birthDate(value: string) {
  return toLocalInput(value).slice(0, 10);
}

function elapsedMinutes(value?: string) {
  return value ? Math.max(0, Math.floor((now.value - new Date(value).getTime()) / 60_000)) : undefined;
}

function stopClock() {
  if (!clock) return;
  clearInterval(clock);
  clock = undefined;
}
</script>

<template>
  <AppPage>
    <view class="page-shell home-page">
      <view class="home-head">
        <view>
          <text class="home-head__brand">Budlog</text>
          <text class="home-head__subtitle">记录宝宝的每一天 <text class="home-head__heart">♥</text></text>
        </view>
        <view class="home-head__actions">
          <button class="icon-btn home-head__bell" aria-label="查看任务" title="查看任务" @click="redirect('/pages/tasks/index')">
            <BellRing :size="20" />
            <text v-if="dashboard?.tasks.length" class="home-head__badge" />
          </button>
          <image class="home-head__avatar" src="/static/ui/baby-avatar.png" mode="aspectFill" />
        </view>
      </view>

      <AppLoading v-if="loading" copy="正在整理今天的记录" />
      <ErrorState v-else-if="error" title="首页暂时无法加载" :copy="error" @retry="load" />
      <view v-else-if="dashboard && !dashboard.configured" class="state-panel surface setup-state">
        <view class="state-panel__icon"><Baby :size="22" /></view>
        <text class="state-panel__title">先添加宝宝资料</text>
        <text class="state-panel__copy">完成昵称和出生时间设置后，即可开始记录日常</text>
        <NButton type="primary" size="large" @click="redirect('/pages/settings/index')">开始设置</NButton>
      </view>

      <template v-else-if="dashboard?.baby">
        <view v-if="dueReminders.length" class="reminder-carousel">
          <view class="reminder-carousel__head">
            <view class="reminder-carousel__title"><BellRing :size="16" /><text>待办提醒</text></view>
            <text class="reminder-carousel__count">{{ reminderIndex + 1 }}/{{ dueReminders.length }}</text>
          </view>
          <swiper
            class="reminder-swiper"
            :autoplay="dueReminders.length > 1"
            :circular="dueReminders.length > 1"
            :current="reminderIndex"
            :duration="350"
            :interval="4000"
            @change="onReminderChange"
          >
            <swiper-item v-for="task in dueReminders" :key="task.id">
              <view class="reminder-slide surface" :class="{ 'reminder-slide--overdue': task.overdue }">
                <button class="reminder-slide__body" @click="go(`/pages/task-edit/index?id=${task.id}`)">
                  <text class="reminder-slide__title">{{ task.title }}</text>
                  <text class="reminder-slide__meta">{{ reminderTiming(task) }}</text>
                </button>
                <button
                  class="reminder-slide__complete"
                  :disabled="completingReminderId === task.id"
                  aria-label="完成任务"
                  title="完成任务"
                  @click="completeReminder(task)"
                >
                  <Check :size="18" />
                </button>
              </view>
            </swiper-item>
          </swiper>
        </view>

        <view class="baby-card surface">
          <view class="baby-card__hero">
            <view class="baby-card__copy">
              <text class="baby-card__eyebrow">嗨，宝贝</text>
              <text class="baby-card__name">{{ dashboard.baby.name }}</text>
              <text class="baby-card__wish">今天也在好好长大呀！</text>
            </view>
            <image class="baby-card__art" src="/static/ui/baby-hero.png" mode="aspectFit" />
          </view>
          <view class="baby-card__age">
            <view class="baby-card__age-item">
              <text class="baby-card__age-value">{{ calendarAgeDays }} 天</text>
              <text class="baby-card__age-label">已出生</text>
            </view>
            <view class="baby-card__age-item">
              <text class="baby-card__age-value">{{ dashboard.baby.ageMonths }} 个月 {{ dashboard.baby.ageRemainingDays }} 天</text>
              <text class="baby-card__age-label">自然月龄</text>
            </view>
          </view>
          <view class="baby-card__birth">
            <CalendarDays :size="16" />
            <text>{{ birthDate(dashboard.baby.birthTime) }} 出生</text>
          </view>
        </view>

        <view v-if="highlightedMilestones.length" class="milestone-grid">
          <button
            v-for="(item, index) in highlightedMilestones"
            :key="`${item.code}-${item.id || item.targetTime}`"
            class="milestone-card surface"
            :class="{ 'milestone-card--green': index === 1 }"
            @click="go('/pages/calendar/index')"
          >
            <view class="milestone-card__icon"><CalendarDays :size="20" /></view>
            <view class="milestone-card__body">
              <text class="milestone-card__title">{{ item.title }}</text>
              <text class="milestone-card__days">{{ milestoneLabel(item.daysDifference) }}</text>
              <text class="milestone-card__date">{{ formatDate(item.targetTime) }}</text>
            </view>
          </button>
        </view>

        <view class="status-grid">
          <view class="status-card surface">
            <view class="status-card__top">
              <view class="status-card__icon status-card__icon--feeding"><Milk :size="20" /></view>
              <text class="status-card__label">上次喂奶</text>
            </view>
            <text class="status-card__value">{{ formatDuration(feedingMinutes) }}</text>
            <text class="status-card__meta">{{ nextFeedingText }}</text>
          </view>
          <view class="status-card surface">
            <view class="status-card__top">
              <view class="status-card__icon status-card__icon--diaper"><Droplets :size="20" /></view>
              <text class="status-card__label">最近尿便</text>
            </view>
            <text class="status-card__value">尿 {{ formatDuration(peeMinutes) }}</text>
            <text class="status-card__meta">便 {{ formatDuration(poopMinutes) }}</text>
          </view>
        </view>

        <view class="quick-grid">
          <button class="quick-action quick-action--pink" @click="go('/pages/feeding/index?mode=timer')"><Timer :size="23" /><text>喂奶</text></button>
          <button class="quick-action quick-action--blue" @click="go('/pages/feeding/index?mode=bottle')"><Milk :size="23" /><text>瓶喂</text></button>
          <button class="quick-action quick-action--green" @click="go('/pages/milk-storage/index')"><PackagePlus :size="23" /><text>存奶</text></button>
          <button class="quick-action quick-action--yellow" @click="go('/pages/diaper/index')"><Droplets :size="23" /><text>尿便</text></button>
          <button class="quick-action quick-action--purple" @click="go('/pages/weight/index')"><Scale :size="23" /><text>体重</text></button>
          <button class="quick-action quick-action--cyan" @click="go('/pages/event/index')"><NotebookPen :size="23" /><text>事件</text></button>
          <button class="quick-action quick-action--rose" @click="go('/pages/task-edit/index')"><SquareCheckBig :size="23" /><text>任务</text></button>
          <button class="quick-action quick-action--mint" @click="go('/pages/calendar/index')"><CalendarDays :size="23" /><text>日历</text></button>
        </view>

        <button class="white-noise-entry surface" @click="go('/pages/white-noise/index')">
          <image src="/static/ui/baby-sleep.png" mode="aspectFill" />
          <view class="white-noise-entry__body">
            <view class="white-noise-entry__title"><AudioLines :size="18" /><text>白噪音</text></view>
            <text class="white-noise-entry__copy">给日常添一点安静</text>
          </view>
          <ChevronRight :size="18" />
        </button>

        <view class="section today-section">
          <view class="section-title">
            <text class="section-title__text">今日摘要</text>
            <button class="section-link" @click="redirect('/pages/records/index')">查看更多 <ChevronRight :size="14" /></button>
          </view>
          <view class="summary-card surface">
            <view class="summary-card__metric summary-card__metric--pink"><Milk :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.feedingCount }}</text><text class="summary-card__label">喂奶</text></view>
            <view class="summary-card__metric summary-card__metric--blue"><Milk :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.bottleAmountMl }}</text><text class="summary-card__label">毫升</text></view>
            <view class="summary-card__metric summary-card__metric--slate"><Timer :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.directFeedingMinutes }}</text><text class="summary-card__label">亲喂(分钟)</text></view>
            <view class="summary-card__metric summary-card__metric--yellow"><Droplets :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.peeCount }}/{{ dashboard.todaySummary.poopCount }}</text><text class="summary-card__label">尿/便</text></view>
          </view>
          <view class="summary-detail-grid">
            <button class="summary-detail surface" @click="go('/pages/milk-storage/index')">
              <view class="summary-detail__icon summary-detail__icon--green"><PackagePlus :size="21" /></view>
              <view><text class="summary-detail__label">存奶 {{ dashboard.todaySummary.milkStorageCount }} 次</text><text class="summary-detail__value">{{ dashboard.todaySummary.storedMilkAmountMl }} ml</text></view>
            </button>
            <button class="summary-detail surface" @click="go('/pages/weight/index')">
              <view class="summary-detail__icon summary-detail__icon--purple"><Scale :size="21" /></view>
              <view><text class="summary-detail__label">体重</text><text class="summary-detail__value">{{ dashboard.todaySummary.weightKg ?? '--' }} kg</text></view>
            </button>
          </view>
        </view>
      </template>

      <AppNav current="home" />
    </view>
  </AppPage>
</template>

<style scoped>
.home-page { padding-top: max(22px, env(safe-area-inset-top)); }
.home-head { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 15px; padding: 0 5px; }
.home-head__brand, .home-head__subtitle { display: block; }
.home-head__brand { font-size: 23px; line-height: 29px; font-weight: 850; }
.home-head__subtitle { color: var(--bud-color-muted); font-size: 12px; line-height: 18px; }
.home-head__heart { color: var(--bud-color-primary); }
.home-head__actions { display: flex; align-items: center; gap: 11px; }
.home-head__bell { position: relative; overflow: visible; }
.home-head__badge { position: absolute; top: 4px; right: 3px; width: 8px; height: 8px; border: 2px solid var(--bud-color-canvas); border-radius: 50%; background: var(--bud-color-primary); }
.home-head__avatar { width: 48px; height: 48px; border-radius: 50%; background: #fff0f4; }
.reminder-carousel { margin-bottom: 11px; }
.reminder-carousel__head { display: flex; min-height: 27px; align-items: center; justify-content: space-between; padding: 0 4px; }
.reminder-carousel__title { display: flex; align-items: center; gap: 6px; color: var(--bud-color-primary); font-size: 13px; font-weight: 800; }
.reminder-carousel__count { color: var(--bud-color-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.reminder-swiper { width: 100%; height: 82px; }
.reminder-slide { display: grid; min-width: 0; height: 76px; grid-template-columns: minmax(0, 1fr) 38px; align-items: center; gap: 8px; padding: 10px 10px 10px 13px; border-color: #ffd5e2; background: #fff8fa; }
.reminder-slide--overdue { border-color: #f5c8c8; background: #fff8f8; }
.reminder-slide__body { display: block; min-width: 0; margin: 0; padding: 0; border: 0; background: transparent; text-align: left; line-height: 1.35; }
.reminder-slide__title { display: -webkit-box; overflow: hidden; color: var(--bud-color-ink); font-size: 13px; font-weight: 800; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.reminder-slide__meta { display: block; margin-top: 4px; overflow: hidden; color: var(--bud-color-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.reminder-slide--overdue .reminder-slide__meta { color: #c54b57; }
.reminder-slide__complete { display: flex; width: 34px; height: 34px; align-items: center; justify-content: center; margin: 0; padding: 0; border: 1px solid #f5adc4; border-radius: 50%; color: var(--bud-color-primary); background: #fff; }
.reminder-slide__complete:disabled { opacity: 0.55; }
.setup-state { min-height: 360px; }
.baby-card { overflow: hidden; border-color: #f8e8ef; }
.baby-card__hero { position: relative; min-height: 126px; overflow: hidden; padding: 17px 18px; background: linear-gradient(110deg, #fff3f7 0%, #ffe3ed 100%); }
.baby-card__copy { position: relative; z-index: 1; max-width: 54%; }
.baby-card__eyebrow, .baby-card__name, .baby-card__wish { display: block; }
.baby-card__eyebrow { color: #f39bb5; font-size: 11px; font-weight: 700; }
.baby-card__name { margin-top: 2px; overflow-wrap: anywhere; font-size: 26px; line-height: 34px; font-weight: 850; }
.baby-card__wish { margin-top: 3px; color: var(--bud-color-muted); font-size: 12px; white-space: nowrap; }
.baby-card__art { position: absolute; right: 0; bottom: 0; width: 145px; height: 116px; -webkit-mask-image: radial-gradient(ellipse at 72% 58%, #000 58%, transparent 88%); mask-image: radial-gradient(ellipse at 72% 58%, #000 58%, transparent 88%); }
.baby-card__age { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 13px 0 11px; }
.baby-card__age-item { min-width: 0; padding: 0 16px; }
.baby-card__age-item + .baby-card__age-item { border-left: 1px solid var(--bud-color-line-soft); }
.baby-card__age-value, .baby-card__age-label { display: block; }
.baby-card__age-value { overflow-wrap: anywhere; font-size: 17px; line-height: 24px; font-weight: 800; }
.baby-card__age-label { margin-top: 1px; color: var(--bud-color-muted); font-size: 11px; }
.baby-card__birth { display: flex; align-items: center; gap: 8px; margin: 0 15px; padding: 10px 0 12px; border-top: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 11px; }
.baby-card__birth .lucide { color: var(--bud-color-primary); }
.milestone-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 11px; }
.milestone-card { display: grid; min-width: 0; grid-template-columns: 32px minmax(0, 1fr); gap: 8px; margin: 0; padding: 12px 10px; text-align: left; line-height: 1.35; }
.milestone-card__icon { display: flex; width: 30px; height: 30px; align-items: center; justify-content: center; border-radius: 7px; color: var(--bud-color-primary); background: var(--bud-color-primary-soft); }
.milestone-card--green .milestone-card__icon { color: var(--baby-green); background: var(--baby-green-soft); }
.milestone-card__body { min-width: 0; }
.milestone-card__title, .milestone-card__days, .milestone-card__date { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.milestone-card__title { font-size: 13px; font-weight: 750; }
.milestone-card__days { margin-top: 2px; font-size: 12px; font-weight: 750; }
.milestone-card__date { margin-top: 1px; color: var(--bud-color-muted); font-size: 9px; }
.quick-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; margin-top: 15px; }
.quick-action { display: flex; min-width: 0; min-height: 66px; flex-direction: column; align-items: center; justify-content: center; gap: 7px; margin: 0; padding: 7px 2px; border: 1px solid transparent; border-radius: 8px; font-size: 11px; font-weight: 700; }
.quick-action--pink { color: #f64078; background: #fff0f5; border-color: #ffdbe7; }
.quick-action--blue { color: #267dea; background: #edf5ff; border-color: #d5e7fc; }
.quick-action--green { color: #1bad75; background: #eafbf4; border-color: #d1f2e5; }
.quick-action--yellow { color: #df8600; background: #fff7e7; border-color: #fbe9cb; }
.quick-action--purple { color: #7856e5; background: #f3efff; border-color: #e4ddfc; }
.quick-action--cyan { color: #168a9c; background: #eaf9fb; border-color: #d1ecf0; }
.quick-action--rose { color: #ee3b76; background: #fff0f5; border-color: #fddae6; }
.quick-action--mint { color: #20a975; background: #eafff5; border-color: #d2f5e6; }
.white-noise-entry { display: grid; width: 100%; min-height: 70px; grid-template-columns: 52px minmax(0, 1fr) auto; align-items: center; gap: 11px; margin: 11px 0 0; padding: 8px 12px 8px 8px; text-align: left; }
.white-noise-entry image { width: 52px; height: 52px; border-radius: 7px; background: var(--bud-color-primary-soft); }
.white-noise-entry__body { min-width: 0; }
.white-noise-entry__title { display: flex; align-items: center; gap: 6px; color: var(--bud-color-ink); font-size: 14px; font-weight: 800; }
.white-noise-entry__title .lucide { color: var(--bud-color-primary); }
.white-noise-entry__copy { display: block; margin-top: 3px; color: var(--bud-color-muted); font-size: 11px; }
.white-noise-entry > .lucide { color: var(--bud-color-muted); }
.status-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
.status-card { min-width: 0; min-height: 124px; padding: 13px; }
.status-card__top { display: flex; align-items: center; gap: 9px; }
.status-card__icon { display: flex; width: 36px; height: 36px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 8px; }
.status-card__icon--feeding { color: #d55a61; background: #ffe9e7; }
.status-card__icon--diaper { color: #317f91; background: #e2f3f7; }
.status-card__label, .status-card__value, .status-card__meta { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-card__label { color: var(--bud-color-muted); font-size: 12px; font-weight: 750; }
.status-card__value { margin-top: 13px; font-size: 16px; line-height: 22px; font-weight: 800; }
.status-card__meta { margin-top: 3px; color: var(--bud-color-muted); font-size: 11px; line-height: 18px; }
.today-section { margin-top: 17px; }
.section-link { display: inline-flex; align-items: center; gap: 1px; margin: 0; padding: 4px 0; color: var(--bud-color-muted); background: transparent; font-size: 11px; line-height: 18px; }
.summary-card { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 12px 5px; }
.summary-card__metric { display: flex; min-width: 0; flex-direction: column; align-items: center; gap: 2px; padding: 1px 3px; border-right: 1px solid var(--bud-color-line-soft); }
.summary-card__metric:last-child { border-right: 0; }
.summary-card__metric--pink { color: #f64078; }
.summary-card__metric--blue { color: #267dea; }
.summary-card__metric--slate { color: #52637f; }
.summary-card__metric--yellow { color: #df8600; }
.summary-card__value { color: var(--bud-color-ink); font-size: 15px; line-height: 20px; font-weight: 800; }
.summary-card__label { max-width: 100%; overflow: hidden; color: var(--bud-color-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.summary-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.summary-detail { display: flex; min-width: 0; min-height: 76px; align-items: flex-start; gap: 9px; margin: 0; padding: 12px 10px; text-align: left; }
.summary-detail__icon { display: flex; width: 31px; height: 31px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 7px; }
.summary-detail__icon--green { color: var(--baby-green); background: var(--baby-green-soft); }
.summary-detail__icon--purple { color: var(--baby-purple); background: var(--baby-purple-soft); }
.summary-detail__label, .summary-detail__value { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-detail__label { font-size: 11px; font-weight: 700; }
.summary-detail__value { margin-top: 4px; font-size: 14px; font-weight: 800; }
@media (max-width: 350px) {
  .baby-card__art { right: -16px; }
  .baby-card__wish { white-space: normal; }
  .quick-grid { gap: 6px; }
  .status-grid { gap: 7px; }
  .status-card { padding: 11px; }
  .status-card__value { font-size: 14px; }
}
</style>
