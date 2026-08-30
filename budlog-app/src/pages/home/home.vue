<script setup lang="ts">
import { BellRing, Check, ChevronRight, Droplets, Milk, Plus, Timer, Toilet } from "lucide-vue-next";
import { onHide, onShow } from "@dcloudio/uni-app";
import { computed, onUnmounted, ref } from "vue";
import { api } from "../../api";
import AppNav from "../../components/AppNav.vue";
import TimelineList from "../../components/TimelineList.vue";
import type { Dashboard, TimelineItem } from "../../types";
import { formatDateTime, formatDuration, formatTime, setAppTimezone, todayKey } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

const dashboard = ref<Dashboard>();
const loading = ref(true);
const error = ref("");
const now = ref(Date.now());
let clock: ReturnType<typeof setInterval> | undefined;

const feedingMinutes = computed(() => {
  const value = dashboard.value?.lastFeeding?.startTime;
  return value ? Math.max(0, Math.floor((now.value - new Date(value).getTime()) / 60_000)) : undefined;
});

const nextFeedingText = computed(() => {
  const value = dashboard.value?.nextExpectedFeedingTime;
  if (!value) return "暂无预计时间";
  const minutes = Math.ceil((new Date(value).getTime() - now.value) / 60_000);
  return minutes >= 0 ? `还有 ${formatDuration(minutes)}` : `已超时 ${formatDuration(Math.abs(minutes))}`;
});

onShow(async () => {
  if (!(await ensureAccess())) return;
  await load();
  if (clock) clearInterval(clock);
  clock = setInterval(() => (now.value = Date.now()), 60_000);
});

onHide(() => {
  if (clock) clearInterval(clock);
});

onUnmounted(() => {
  if (clock) clearInterval(clock);
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    dashboard.value = await api.dashboard();
    if (dashboard.value.baby) setAppTimezone(dashboard.value.baby.timezone);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "首页加载失败";
  } finally {
    loading.value = false;
  }
}

function go(url: string) {
  uni.navigateTo({ url });
}

function redirect(url: string) {
  uni.redirectTo({ url });
}

function editTimeline(item: TimelineItem) {
  if (item.category === "FEEDING") go(`/pages/feeding/feeding?id=${item.id}`);
  if (item.category === "DIAPER") go(`/pages/diaper/diaper?id=${item.id}`);
}

async function completeTask(id: number) {
  try {
    await api.updateTaskStatus(id, "DONE");
    clearShownReminder(id);
    await load();
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "任务更新失败", icon: "none" });
  }
}

function milestoneLabel(days: number) {
  if (days === 0) return "就是今天";
  return days > 0 ? `还有 ${days} 天` : `已过去 ${Math.abs(days)} 天`;
}
</script>

<template>
  <view class="page-shell home-page">
    <view class="home-head">
      <view>
        <text class="home-head__brand">Budlog</text>
        <text class="home-head__date">{{ dashboard?.date || todayKey() }}</text>
      </view>
      <button class="icon-btn home-head__bell" aria-label="任务" @click="redirect('/pages/tasks/tasks')">
        <BellRing :size="21" />
        <text v-if="dashboard?.tasks.length" class="home-head__badge">{{ dashboard.tasks.length }}</text>
      </button>
    </view>

    <view v-if="loading" class="surface empty-state">加载中</view>
    <view v-else-if="error" class="surface empty-state">
      <text class="danger-text">{{ error }}</text>
      <button class="btn btn--secondary retry-btn" @click="load">重试</button>
    </view>
    <view v-else-if="dashboard && !dashboard.configured" class="surface setup-state">
      <text class="setup-state__title">设置宝宝资料</text>
      <button class="btn btn--primary" @click="redirect('/pages/settings/settings')">开始设置</button>
    </view>

    <template v-else-if="dashboard?.baby">
      <view class="baby-status surface">
        <view>
          <text class="baby-status__name">{{ dashboard.baby.name }}</text>
          <text class="baby-status__age">出生第 {{ dashboard.baby.ageDayNumber }} 天</text>
        </view>
        <view class="baby-status__duration">
          <text>{{ dashboard.baby.ageDurationDays }} 天 {{ dashboard.baby.ageDurationHours }} 小时</text>
          <text class="muted">{{ dashboard.baby.ageMonths }} 个月 {{ dashboard.baby.ageRemainingDays }} 天</text>
        </view>
      </view>

      <view v-if="dashboard.nextMilestone" class="milestone-band">
        <view>
          <text class="milestone-band__eyebrow">下一个关键节点</text>
          <text class="milestone-band__title">{{ dashboard.nextMilestone.title }}</text>
        </view>
        <view class="milestone-band__right">
          <text class="milestone-band__days">{{ milestoneLabel(dashboard.nextMilestone.daysDifference) }}</text>
          <text class="milestone-band__date">{{ formatDateTime(dashboard.nextMilestone.targetTime) }}</text>
        </view>
      </view>

      <view class="status-grid section">
        <view class="status-card surface">
          <view class="status-card__icon status-card__icon--feeding"><Milk :size="20" /></view>
          <text class="status-card__label">上次喂奶</text>
          <text class="status-card__value">{{ feedingMinutes === undefined ? "暂无" : formatDuration(feedingMinutes) }}</text>
          <text class="status-card__meta">{{ nextFeedingText }}</text>
        </view>
        <view class="status-card surface">
          <view class="status-card__icon status-card__icon--diaper"><Droplets :size="20" /></view>
          <text class="status-card__label">最近尿便</text>
          <view class="status-card__value status-card__value--split">
            <text>尿</text>
            <text class="status-card__duration">{{ formatDuration(dashboard.lastPeeMinutesAgo) }}</text>
          </view>
          <text class="status-card__meta">便 {{ formatDuration(dashboard.lastPoopMinutesAgo) }}</text>
        </view>
      </view>

      <view class="section">
        <view class="section-title"><text class="section-title__text">快速记录</text></view>
        <view class="quick-grid">
          <button class="quick-action quick-action--direct" @click="go('/pages/feeding/feeding?mode=timer')">
            <Timer :size="23" /><text>亲喂</text>
          </button>
          <button class="quick-action quick-action--bottle" @click="go('/pages/feeding/feeding?mode=bottle')">
            <Milk :size="23" /><text>瓶喂</text>
          </button>
          <button class="quick-action quick-action--pee" @click="go('/pages/diaper/diaper?type=PEE')">
            <Droplets :size="23" /><text>尿尿</text>
          </button>
          <button class="quick-action quick-action--poop" @click="go('/pages/diaper/diaper?type=POOP')">
            <Toilet :size="23" /><text>便便</text>
          </button>
        </view>
      </view>

      <view class="section">
        <view class="section-title"><text class="section-title__text">今日摘要</text></view>
        <view class="summary-strip surface">
          <view><text class="summary-strip__value">{{ dashboard.todaySummary.feedingCount }}</text><text>喂奶</text></view>
          <view><text class="summary-strip__value">{{ dashboard.todaySummary.bottleAmountMl }}</text><text>毫升</text></view>
          <view><text class="summary-strip__value">{{ dashboard.todaySummary.peeCount }}</text><text>尿尿</text></view>
          <view><text class="summary-strip__value">{{ dashboard.todaySummary.poopCount }}</text><text>便便</text></view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">
          <text class="section-title__text">待办</text>
          <button class="btn btn--ghost" @click="go('/pages/task-edit/task-edit')"><Plus :size="17" />新增</button>
        </view>
        <view v-if="dashboard.tasks.length" class="task-preview surface">
          <view v-for="task in dashboard.tasks" :key="task.id" class="task-preview__row">
            <button class="task-preview__check" aria-label="完成任务" @click="completeTask(task.id)"><Check :size="18" /></button>
            <view class="task-preview__body" @click="go(`/pages/task-edit/task-edit?id=${task.id}`)">
              <text class="task-preview__title">{{ task.title }}</text>
              <text :class="task.overdue ? 'danger-text' : 'muted'">{{ formatDateTime(task.dueTime) }}</text>
            </view>
            <ChevronRight :size="18" class="muted" />
          </view>
        </view>
        <view v-else class="empty-state surface">今天没有待办</view>
      </view>

      <view class="section">
        <view class="section-title">
          <text class="section-title__text">最近记录</text>
          <button class="btn btn--ghost" @click="redirect('/pages/records/records')">全部<ChevronRight :size="17" /></button>
        </view>
        <TimelineList :items="dashboard.recentTimeline" editable @edit="editTimeline" />
      </view>
    </template>

    <AppNav current="home" />
  </view>
</template>

<style scoped>
.home-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 18px;
}

.home-head__brand,
.home-head__date {
  display: block;
}

.home-head__brand {
  font-size: 24px;
  font-weight: 800;
}

.home-head__date {
  margin-top: 1px;
  color: #74807b;
  font-size: 12px;
}

.home-head__bell {
  position: relative;
  background: #ffffff;
  border: 1px solid #dfe5e1;
}

.home-head__badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 2px solid #f4f6f5;
  border-radius: 9px;
  color: #ffffff;
  background: #b34a41;
  font-size: 10px;
  line-height: 14px;
}

.retry-btn {
  margin: 14px auto 0;
}

.setup-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}

.setup-state__title {
  font-size: 18px;
  font-weight: 700;
}

.baby-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.baby-status__name,
.baby-status__age,
.baby-status__duration text {
  display: block;
}

.baby-status__name {
  font-size: 21px;
  font-weight: 800;
}

.baby-status__age {
  margin-top: 3px;
  color: #216454;
  font-weight: 700;
}

.baby-status__duration {
  text-align: right;
  line-height: 22px;
}

.milestone-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 10px;
  padding: 14px 16px;
  border-left: 4px solid #c6922d;
  background: #fff9e8;
}

.milestone-band__eyebrow,
.milestone-band__title,
.milestone-band__days,
.milestone-band__date {
  display: block;
}

.milestone-band__eyebrow,
.milestone-band__date {
  color: #78683a;
  font-size: 12px;
}

.milestone-band__title,
.milestone-band__days {
  font-weight: 750;
}

.milestone-band__right {
  text-align: right;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.status-card {
  min-width: 0;
  min-height: 150px;
  padding: 14px;
}

.status-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-bottom: 12px;
  border-radius: 6px;
}

.status-card__icon--feeding {
  color: #a64b3f;
  background: #fbe9e4;
}

.status-card__icon--diaper {
  color: #176e78;
  background: #e2f1f2;
}

.status-card__label,
.status-card__value,
.status-card__meta {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-card__label {
  color: #68746f;
  font-size: 12px;
}

.status-card__value {
  margin-top: 3px;
  overflow: visible;
  font-size: 17px;
  font-weight: 750;
  line-height: 22px;
  text-overflow: clip;
  white-space: normal;
  word-break: keep-all;
}

.status-card__value--split {
  display: flex;
  flex-wrap: wrap;
  column-gap: 4px;
}

.status-card__duration {
  max-width: 100%;
  white-space: nowrap;
}

.status-card__meta {
  margin-top: 4px;
  color: #68746f;
  font-size: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 0;
  height: 82px;
  margin: 0;
  padding: 6px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 700;
}

.quick-action--direct { color: #713e35; background: #f8e6e0; }
.quick-action--bottle { color: #305c4e; background: #e2efe9; }
.quick-action--pee { color: #176e78; background: #e2f1f2; }
.quick-action--poop { color: #6b571b; background: #f5edca; }

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 15px 8px;
}

.summary-strip > view {
  min-width: 0;
  border-right: 1px solid #e7ebe9;
  color: #6b7772;
  font-size: 11px;
  text-align: center;
}

.summary-strip > view:last-child { border-right: 0; }
.summary-strip text { display: block; }

.summary-strip__value {
  margin-bottom: 2px;
  color: #18201d;
  font-size: 19px;
  font-weight: 800;
}

.task-preview { overflow: hidden; }

.task-preview__row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 20px;
  gap: 8px;
  align-items: center;
  min-height: 62px;
  padding: 8px 10px;
  border-bottom: 1px solid #edf0ee;
}

.task-preview__row:last-child { border-bottom: 0; }

.task-preview__check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: 1px solid #96aaa1;
  border-radius: 6px;
  color: #216454;
  background: #ffffff;
}

.task-preview__body { min-width: 0; }
.task-preview__body text { display: block; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.task-preview__title { color: #18201d; font-size: 14px !important; font-weight: 650; }
</style>
