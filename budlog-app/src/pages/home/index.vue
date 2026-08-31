<script setup lang="ts">
import { AlertCircle, Baby, BellRing, CalendarDays, Check, ChevronRight, Droplets, Heart, Milk, NotebookPen, PackagePlus, Plus, Scale, Sparkles, Timer } from "lucide-vue-next";
import { onHide, onShow } from "@dcloudio/uni-app";
import { computed, onUnmounted, ref } from "vue";
import { api } from "../../api";
import AppNav from "../../components/AppNav.vue";
import BrandMark from "../../components/BrandMark.vue";
import TimelineList from "../../components/TimelineList.vue";
import type { Dashboard, TimelineItem } from "../../types";
import { formatDate, formatDateTime, formatDuration, formatTime, setAppTimezone, todayKey } from "../../utils/date";
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

const calendarAgeDays = computed(() => Math.max(0, (dashboard.value?.baby?.ageDayNumber ?? 1) - 1));

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
  if (item.category === "FEEDING") go(`/pages/feeding/index?id=${item.id}`);
  if (item.category === "MILK_STORAGE") go(`/pages/milk-storage/index?id=${item.id}`);
  if (item.category === "DIAPER") go(`/pages/diaper/index?id=${item.id}`);
  if (item.category === "WEIGHT") go(`/pages/weight/index?id=${item.id}`);
  if (item.category === "EVENT") go(`/pages/event/index?id=${item.id}`);
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
      <view class="home-head__brand-lockup">
        <BrandMark />
        <view>
          <text class="home-head__brand">Budlog</text>
          <text class="home-head__date">{{ dashboard?.date || todayKey() }} · 家庭育儿日记</text>
        </view>
      </view>
      <button class="icon-btn home-head__bell" aria-label="任务" title="查看任务" @click="redirect('/pages/tasks/index')">
        <BellRing :size="21" />
        <text v-if="dashboard?.tasks.length" class="home-head__badge">{{ dashboard.tasks.length }}</text>
      </button>
    </view>

    <view v-if="loading" class="state-panel surface">
      <wd-loading color="#b94b5d" />
      <text class="state-panel__copy">正在整理今天的记录</text>
    </view>
    <view v-else-if="error" class="state-panel surface">
      <view class="state-panel__icon state-panel__icon--error"><AlertCircle :size="22" /></view>
      <text class="state-panel__title">首页暂时无法加载</text>
      <text class="state-panel__copy">{{ error }}</text>
      <wd-button type="info" size="medium" @click="load">重新加载</wd-button>
    </view>
    <view v-else-if="dashboard && !dashboard.configured" class="state-panel surface setup-state">
      <view class="state-panel__icon"><Baby :size="22" /></view>
      <text class="state-panel__title">先添加宝宝资料</text>
      <text class="state-panel__copy">完成昵称和出生时间设置后，即可开始记录日常</text>
      <wd-button type="primary" size="large" @click="redirect('/pages/settings/index')">开始设置</wd-button>
    </view>

    <template v-else-if="dashboard?.baby">
      <view class="baby-overview surface">
        <view class="baby-overview__top">
          <view>
            <view class="baby-overview__eyebrow"><Sparkles :size="14" /><text>宝宝状态</text></view>
            <text class="baby-overview__name">{{ dashboard.baby.name }}</text>
          </view>
          <text class="baby-overview__age-badge">{{ calendarAgeDays }} 日龄</text>
        </view>
        <view class="baby-overview__duration">
          <view>
            <text class="baby-overview__duration-value">{{ calendarAgeDays }} 天</text>
            <text class="baby-overview__duration-label">已出生天数</text>
          </view>
          <view>
            <text class="baby-overview__duration-value">{{ dashboard.baby.ageMonths }} 个月 {{ dashboard.baby.ageRemainingDays }} 天</text>
            <text class="baby-overview__duration-label">自然月龄</text>
          </view>
        </view>
        <view class="baby-overview__warm"><Heart :size="14" /><text>今天也在好好长大</text></view>
      </view>

      <view v-if="dashboard.nextMilestone" class="milestone-band">
        <view class="milestone-band__icon"><CalendarDays :size="19" /></view>
        <view class="milestone-band__body">
          <text class="milestone-band__eyebrow">下一个关键节点</text>
          <text class="milestone-band__title">{{ dashboard.nextMilestone.title }}</text>
        </view>
        <view class="milestone-band__right">
          <text class="milestone-band__days">{{ milestoneLabel(dashboard.nextMilestone.daysDifference) }}</text>
          <text class="milestone-band__date">{{ formatDate(dashboard.nextMilestone.targetTime) }}</text>
        </view>
      </view>

      <view class="section">
        <view class="section-title"><text class="section-title__text">快速记录</text></view>
        <view class="quick-grid">
          <button class="quick-action quick-action--direct" @click="go('/pages/feeding/index?mode=timer')">
            <Timer :size="23" /><text>亲喂</text>
          </button>
          <button class="quick-action quick-action--bottle" @click="go('/pages/feeding/index?mode=bottle')">
            <Milk :size="23" /><text>瓶喂</text>
          </button>
          <button class="quick-action quick-action--storage" @click="go('/pages/milk-storage/index')">
            <PackagePlus :size="23" /><text>存奶</text>
          </button>
          <button class="quick-action quick-action--diaper" @click="go('/pages/diaper/index')">
            <Droplets :size="23" /><text>尿便</text>
          </button>
          <button class="quick-action quick-action--weight" @click="go('/pages/weight/index')">
            <Scale :size="23" /><text>体重</text>
          </button>
          <button class="quick-action quick-action--event" @click="go('/pages/event/index')">
            <NotebookPen :size="23" /><text>事件</text>
          </button>
        </view>
      </view>

      <view class="status-grid section">
        <view class="status-card surface">
          <view class="status-card__top">
            <view class="status-card__icon status-card__icon--feeding"><Milk :size="20" /></view>
            <text class="status-card__label">上次喂奶</text>
          </view>
          <text class="status-card__value">{{ feedingMinutes === undefined ? "暂无" : formatDuration(feedingMinutes) }}</text>
          <text class="status-card__meta">{{ nextFeedingText }}</text>
        </view>
        <view class="status-card surface">
          <view class="status-card__top">
            <view class="status-card__icon status-card__icon--diaper"><Droplets :size="20" /></view>
            <text class="status-card__label">最近尿便</text>
          </view>
          <view class="status-card__value status-card__value--split">
            <text>尿</text>
            <text class="status-card__duration">{{ formatDuration(dashboard.lastPeeMinutesAgo) }}</text>
          </view>
          <text class="status-card__meta">便 {{ formatDuration(dashboard.lastPoopMinutesAgo) }}</text>
        </view>
      </view>

      <view class="section">
        <view class="section-title"><text class="section-title__text">今日摘要</text></view>
        <view class="summary-strip surface">
          <view class="summary-strip__metric"><text class="summary-strip__value">{{ dashboard.todaySummary.feedingCount }}</text><text>喂奶</text></view>
          <view class="summary-strip__metric"><text class="summary-strip__value">{{ dashboard.todaySummary.bottleAmountMl }}</text><text>毫升</text></view>
          <view class="summary-strip__metric"><text class="summary-strip__value">{{ dashboard.todaySummary.peeCount }}</text><text>尿尿</text></view>
          <view class="summary-strip__metric"><text class="summary-strip__value">{{ dashboard.todaySummary.poopCount }}</text><text>便便</text></view>
          <view class="summary-strip__storage">
            <PackagePlus :size="17" />
            <text>存奶 {{ dashboard.todaySummary.milkStorageCount }} 次</text>
            <text class="summary-strip__storage-amount">{{ dashboard.todaySummary.storedMilkAmountMl }} ml</text>
          </view>
          <view class="summary-strip__extra">
            <view><Scale :size="16" /><text>体重 {{ dashboard.todaySummary.weightKg ?? "--" }} kg</text></view>
            <view><NotebookPen :size="16" /><text>事件 {{ dashboard.todaySummary.eventCount }} 条</text></view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">
          <text class="section-title__text">待办</text>
          <button class="section-action" @click="go('/pages/task-edit/index')"><Plus :size="16" />新增</button>
        </view>
        <view v-if="dashboard.tasks.length" class="task-preview surface">
          <view v-for="task in dashboard.tasks" :key="task.id" class="task-preview__row">
            <button class="task-preview__check" aria-label="完成任务" @click="completeTask(task.id)"><Check :size="18" /></button>
            <view class="task-preview__body" @click="go(`/pages/task-edit/index?id=${task.id}`)">
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
          <button class="section-action" @click="redirect('/pages/records/index')">全部<ChevronRight :size="16" /></button>
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
  min-height: 44px;
  margin-bottom: 20px;
}

.home-head__brand-lockup {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.home-head__brand,
.home-head__date {
  display: block;
}

.home-head__brand {
  font-size: 21px;
  line-height: 25px;
  font-weight: 780;
}

.home-head__date {
  margin-top: 1px;
  overflow: hidden;
  color: var(--bud-color-muted);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-head__bell {
  position: relative;
  flex: 0 0 auto;
  overflow: visible;
  border: 1px solid var(--bud-color-line);
  background: #ffffff;
  box-shadow: var(--bud-shadow-sm);
}

.home-head__badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 2px solid var(--bud-color-canvas);
  border-radius: 9px;
  color: #ffffff;
  background: var(--bud-color-primary);
  font-size: 10px;
  line-height: 14px;
}

.state-panel__icon--error {
  color: #a33e43;
  background: var(--bud-color-coral-soft);
}

.setup-state {
  min-height: 300px;
}

.baby-overview {
  padding: 18px;
  border-color: #ecd6d9;
  background: #fffdfd;
}

.baby-overview__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.baby-overview__eyebrow,
.baby-overview__name,
.baby-overview__duration-value,
.baby-overview__duration-label {
  display: block;
}

.baby-overview__eyebrow {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 2px;
  color: var(--bud-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.baby-overview__name {
  font-size: 27px;
  line-height: 35px;
  font-weight: 780;
}

.baby-overview__age-badge {
  margin-top: 3px;
  padding: 5px 8px;
  border-radius: 6px;
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-primary-soft);
  font-size: 12px;
  font-weight: 700;
}

.baby-overview__duration {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--bud-color-line-soft);
}

.baby-overview__duration > view + view {
  padding-left: 16px;
  border-left: 1px solid var(--bud-color-line-soft);
}

.baby-overview__duration-value {
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 680;
  line-height: 22px;
}

.baby-overview__duration-label {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 11px;
}

.baby-overview__warm {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 13px;
  color: var(--bud-color-sage);
  font-size: 12px;
  font-weight: 650;
}

.milestone-band {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 13px 14px;
  border: 1px solid #f0dca6;
  border-radius: 8px;
  background: #fffbf0;
}

.milestone-band__icon {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: var(--bud-color-gold);
  background: var(--bud-color-gold-soft);
}

.milestone-band__body {
  min-width: 0;
}

.milestone-band__eyebrow,
.milestone-band__title,
.milestone-band__days,
.milestone-band__date {
  display: block;
}

.milestone-band__eyebrow,
.milestone-band__date {
  color: #856c3e;
  font-size: 11px;
}

.milestone-band__title,
.milestone-band__days {
  font-weight: 750;
}

.milestone-band__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  min-height: 138px;
  padding: 14px 15px;
}

.status-card__top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.status-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 7px;
}

.status-card__icon--feeding {
  color: var(--bud-color-coral);
  background: var(--bud-color-coral-soft);
}

.status-card__icon--diaper {
  color: var(--bud-color-cyan);
  background: var(--bud-color-cyan-soft);
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
  color: var(--bud-color-muted);
  font-size: 12px;
  font-weight: 650;
}

.status-card__value {
  margin-top: 13px;
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
  color: var(--bud-color-muted);
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
  height: 84px;
  margin: 0;
  padding: 6px;
  border: 1px solid transparent;
  border-radius: 8px;
  box-shadow: var(--bud-shadow-sm);
  font-size: 13px;
  font-weight: 700;
  transition: box-shadow 0.16s ease, transform 0.16s ease;
}

.quick-action:active { transform: translateY(1px) scale(0.97); box-shadow: none; }
.quick-action--direct { color: #99463f; border-color: #f0d1cc; background: var(--bud-color-coral-soft); }
.quick-action--bottle { color: #356957; border-color: #cfe4da; background: var(--bud-color-sage-soft); }
.quick-action--storage { color: #47705e; border-color: #cfe4da; background: #eef7f2; }
.quick-action--diaper { color: #2d7081; border-color: #cee5ea; background: var(--bud-color-cyan-soft); }
.quick-action--weight { color: #476779; border-color: #d3e0e5; background: #eef4f6; }
.quick-action--event { color: #72556f; border-color: #e4d4e1; background: #f5eff5; }

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 15px 8px 0;
  overflow: hidden;
}

.summary-strip__metric {
  min-width: 0;
  border-right: 1px solid var(--bud-color-line-soft);
  color: var(--bud-color-muted);
  font-size: 11px;
  text-align: center;
}

.summary-strip__extra {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--bud-color-line-soft);
}

.summary-strip__extra > view {
  display: flex;
  min-width: 0;
  min-height: 40px;
  padding: 8px 6px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--bud-color-muted);
  font-size: 12px;
  font-weight: 650;
}

.summary-strip__extra > view:first-child {
  border-right: 1px solid var(--bud-color-line-soft);
}

@media (min-width: 600px) {
  .quick-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

.summary-strip__metric:nth-child(4) { border-right: 0; }
.summary-strip__metric text { display: block; }

.summary-strip__value {
  margin-bottom: 2px;
  color: var(--bud-color-ink);
  font-size: 19px;
  font-weight: 800;
}

.summary-strip__storage {
  display: flex;
  grid-column: 1 / -1;
  min-width: 0;
  min-height: 43px;
  margin-top: 12px;
  padding: 9px 7px;
  align-items: center;
  gap: 7px;
  border-top: 1px solid var(--bud-color-line-soft);
  color: #47705e;
  font-size: 12px;
  font-weight: 650;
}

.summary-strip__storage > svg { flex: 0 0 auto; }
.summary-strip__storage-amount { margin-left: auto; color: var(--bud-color-ink); font-weight: 750; }

.task-preview { overflow: hidden; }

.task-preview__row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 20px;
  gap: 8px;
  align-items: center;
  min-height: 62px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--bud-color-line-soft);
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
  border: 1px solid #8db5a5;
  border-radius: 50%;
  color: var(--bud-color-sage);
  background: #ffffff;
}

.task-preview__body { min-width: 0; }
.task-preview__body text { display: block; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.task-preview__title { color: var(--bud-color-ink); font-size: 14px !important; font-weight: 650; }

.section-action {
  display: inline-flex;
  min-height: 34px;
  margin: 0;
  padding: 0 4px;
  align-items: center;
  gap: 3px;
  border: 0;
  color: var(--bud-color-primary);
  background: transparent;
  font-size: 13px;
  font-weight: 650;
}

@media (max-width: 360px) {
  .quick-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .milestone-band {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .milestone-band__right {
    grid-column: 2;
    text-align: left;
  }
}
</style>
