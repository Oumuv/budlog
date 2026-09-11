<script setup lang="ts">
import { CalendarDays, ChevronLeft, ChevronRight, RefreshCw } from "lucide-vue-next";
import { NButton } from "naive-ui";
import { onShow, onUnload } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import PageHeader from "../../components/PageHeader.vue";
import type { CalendarItem } from "../../types";
import { calendarDetailUrl, calendarFilters, calendarGroup, calendarItemLabel, monthGrid, shiftMonth } from "../../utils/calendar";
import type { CalendarFilter } from "../../utils/calendar";
import { formatTime, setAppTimezone, shiftDay, todayKey } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const today = ref(todayKey());
const selectedDate = ref(today.value);
const month = ref(today.value.slice(0, 7));
const filter = ref<CalendarFilter>("ALL");
const items = ref<CalendarItem[]>([]);
const loading = ref(true);
const error = ref("");
const needsSetup = ref(false);
const ready = ref(false);
let requestId = 0;
const weekdays = ["一", "二", "三", "四", "五", "六", "日"];
const dates = computed(() => monthGrid(month.value));
const monthTitle = computed(() => `${month.value.slice(0, 4)} 年 ${Number(month.value.slice(5))} 月`);
const visibleItems = computed(() => items.value.filter((item) => filter.value === "ALL" || calendarGroup(item.category) === filter.value));
const byDate = computed(() => {
  const result: Record<string, CalendarItem[]> = {};
  visibleItems.value.forEach((item) => (result[item.date] ||= []).push(item));
  return result;
});
const selectedItems = computed(() => byDate.value[selectedDate.value] || []);
const monthCount = computed(() => visibleItems.value.filter((item) => item.date.startsWith(month.value)).length);
const days = computed(() => dates.value.map((date) => {
  const records = byDate.value[date] || [];
  // Keep tasks and milestones visible even on days with many feeding records.
  const preview = [...records].sort((a, b) => priority(a) - priority(b)).slice(0, 2);
  return { date, number: Number(date.slice(8)), records, preview };
}));

onShow(async () => {
  if (await ensureAccess()) await load();
});
onUnload(() => { requestId += 1; });

async function load() {
  const current = ++requestId;
  loading.value = true;
  error.value = "";
  items.value = [];
  try {
    const baby = await api.getBaby();
    if (current !== requestId) return;
    needsSetup.value = !baby;
    if (!baby) return;
    setAppTimezone(baby.timezone);
    today.value = todayKey();
    if (!ready.value) {
      selectedDate.value = today.value;
      month.value = today.value.slice(0, 7);
      ready.value = true;
    }
    const grid = monthGrid(month.value);
    const result = await api.calendar(grid[0], shiftDay(grid[41], 1));
    if (current !== requestId) return;
    setAppTimezone(result.timezone);
    items.value = result.items;
  } catch (exception) {
    if (current === requestId) error.value = exception instanceof Error ? exception.message : "日历加载失败";
  } finally {
    if (current === requestId) loading.value = false;
  }
}

function priority(item: CalendarItem) {
  return item.category === "MILESTONE" ? 0 : item.category === "TASK" ? 1 : item.category === "EVENT" ? 2 : 3;
}

function changeMonth(value: string) {
  if (!/^\d{4}-\d{2}$/.test(value) || value === month.value) return;
  month.value = value;
  selectedDate.value = value === today.value.slice(0, 7) ? today.value : `${value}-01`;
  void load();
}

function pickMonth(event: { detail: { value: string } }) {
  changeMonth(event.detail.value.slice(0, 7));
}

function selectDay(date: string) {
  selectedDate.value = date;
  if (!date.startsWith(month.value)) {
    month.value = date.slice(0, 7);
    void load();
  }
}

function goToday() {
  today.value = todayKey();
  const changed = month.value !== today.value.slice(0, 7);
  month.value = today.value.slice(0, 7);
  selectedDate.value = today.value;
  if (changed) void load();
}

function openItem(item: CalendarItem) {
  uni.navigateTo({ url: calendarDetailUrl(item) });
}

function goSettings() {
  uni.navigateTo({ url: "/pages/settings/index" });
}
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form calendar-page">
    <PageHeader title="育儿日历" back>
      <button class="icon-btn" aria-label="刷新日历" :disabled="loading" @click="load"><RefreshCw :size="19" /></button>
    </PageHeader>
    <text class="calendar-intro">每天的安排、成长节点与照护记录</text>

    <view v-if="needsSetup" class="state-panel surface">
      <text class="state-panel__title">先添加宝宝资料</text>
      <NButton type="primary" @click="goSettings">前往设置</NButton>
    </view>
    <template v-else>
      <view class="month-toolbar">
        <button class="icon-btn" aria-label="上个月" :disabled="!ready" @click="changeMonth(shiftMonth(month, -1))"><ChevronLeft :size="21" /></button>
        <picker mode="date" fields="month" :value="`${month}-01`" :disabled="!ready" @change="pickMonth">
          <view class="month-title">{{ monthTitle }}</view>
        </picker>
        <button class="icon-btn" aria-label="下个月" :disabled="!ready" @click="changeMonth(shiftMonth(month, 1))"><ChevronRight :size="21" /></button>
        <button class="shortcut-chip" :disabled="!ready" @click="goToday">今天</button>
      </view>

      <view class="calendar-filters">
        <button v-for="option in calendarFilters" :key="option.value" class="shortcut-chip" :class="{ 'shortcut-chip--active': filter === option.value }" :aria-pressed="filter === option.value" @click="filter = option.value">{{ option.label }}</button>
      </view>

      <ErrorState v-if="error" title="日历加载失败" :copy="error" @retry="load" />
      <AppLoading v-else-if="loading" copy="正在整理这个月的事项" />
      <template v-else>
        <view class="calendar surface">
          <view class="calendar-weekdays"><text v-for="weekday in weekdays" :key="weekday">{{ weekday }}</text></view>
          <view class="calendar-grid">
            <view v-for="day in days" :key="day.date" class="calendar-day" :class="{ 'calendar-day--outside': !day.date.startsWith(month), 'calendar-day--selected': day.date === selectedDate }">
              <button class="day-select" :aria-label="`${day.date}，${day.records.length} 条事项`" :aria-pressed="day.date === selectedDate" @click="selectDay(day.date)">
                <text class="day-number" :class="{ 'day-number--today': day.date === today }">{{ day.number }}</text>
                <text class="day-count">{{ day.records.length ? `${day.records.length}条` : '' }}</text>
              </button>
              <button v-for="item in day.preview" :key="item.key" class="day-item" :class="`tone-${calendarGroup(item.category).toLowerCase()}`" :title="item.title" @click="openItem(item)">{{ item.title }}</button>
              <button v-if="day.records.length > 2" class="day-more" :aria-label="`查看 ${day.date} 全部 ${day.records.length} 条事项`" @click="selectDay(day.date)">+{{ day.records.length - 2 }}</button>
            </view>
          </view>
          <view class="calendar-legend">
            <text class="legend-item"><text class="legend-dot tone-task" />任务</text>
            <text class="legend-item"><text class="legend-dot tone-milestone" />节点</text>
            <text class="legend-item"><text class="legend-dot tone-event" />事件</text>
            <text class="legend-item"><text class="legend-dot tone-care" />日常</text>
            <text class="month-count">本月 {{ monthCount }} 条</text>
          </view>
        </view>

        <view class="section-title agenda-heading">
          <view><text class="section-title__text">{{ selectedDate }}{{ selectedDate === today ? ' · 今天' : '' }}</text><text class="page-subtitle">{{ selectedItems.length }} 条事项 · 点击查看详情</text></view>
        </view>
        <view v-if="selectedItems.length" class="agenda-list">
          <button v-for="item in selectedItems" :key="item.key" class="agenda-item surface" @click="openItem(item)">
            <text class="agenda-time">{{ formatTime(item.eventTime) }}</text>
            <view class="agenda-body">
              <text class="agenda-title">{{ item.title }}</text>
              <view class="agenda-meta"><text class="agenda-tag" :class="`tone-${calendarGroup(item.category).toLowerCase()}`">{{ calendarItemLabel(item) }}</text><text v-if="item.subtitle">{{ item.subtitle }}</text></view>
            </view>
            <ChevronRight :size="17" class="muted" />
          </button>
        </view>
        <view v-else class="state-panel surface">
          <view class="state-panel__icon"><CalendarDays :size="22" /></view>
          <text class="state-panel__title">{{ filter === 'ALL' ? '这一天暂无事项' : '这一天暂无此类事项' }}</text>
          <text class="state-panel__copy">可切换日期或分类，查看其他安排与记录</text>
        </view>
      </template>
    </template>
    </view>
  </AppPage>
</template>

<style scoped>
.calendar-intro { display: block; margin: -6px 0 18px; color: var(--bud-color-muted); text-align: center; font-size: 13px; }
.month-toolbar { display: flex; align-items: center; gap: 4px; margin-bottom: 14px; }
.month-toolbar picker { flex: 1; text-align: center; }
.month-title { padding: 10px 0; font-size: 18px; font-weight: 750; }
.calendar-filters { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 16px; }
.calendar-filters .shortcut-chip { padding: 0 10px; font-size: 12px; }
.calendar { overflow: hidden; }
.calendar-weekdays, .calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); }
.calendar-weekdays { padding: 12px 0; background: #fffbfb; color: var(--bud-color-muted); text-align: center; font-size: 12px; }
.calendar-day { min-width: 0; min-height: 104px; padding: 3px 2px; border-top: 1px solid var(--bud-color-line-soft); border-right: 1px solid var(--bud-color-line-soft); }
.calendar-day:nth-child(7n) { border-right: 0; }
.calendar-day--outside { background: #faf7f7; }
.calendar-day--outside .day-number { color: #9b898d; }
.calendar-day--selected { background: #fff0f3; box-shadow: inset 0 0 0 2px var(--bud-color-primary); }
.day-select { display: flex; width: 100%; min-height: 34px; flex-direction: column; align-items: center; margin: 0; padding: 0; background: transparent; line-height: 1.2; }
.day-number { display: inline-flex; align-items: center; justify-content: center; width: 23px; height: 23px; border-radius: 50%; color: var(--bud-color-ink); font-size: 13px; font-weight: 700; }
.day-number--today { color: #fff !important; background: var(--bud-color-primary); }
.day-count { min-height: 12px; color: var(--bud-color-muted); font-size: 9px; }
.day-item { display: block; overflow: hidden; width: 100%; margin: 3px 0 0; padding: 3px 2px; border-radius: 3px; font-size: 10px; line-height: 15px; text-overflow: ellipsis; text-align: left; white-space: nowrap; }
.day-more { width: 100%; margin: 0; padding: 0; color: var(--bud-color-muted); background: transparent; font-size: 10px; line-height: 18px; }
.tone-task { color: var(--baby-blue); background: var(--baby-blue-soft); }
.tone-milestone { color: var(--bud-color-primary-dark); background: var(--bud-color-primary-soft); }
.tone-event { color: var(--baby-green); background: var(--baby-green-soft); }
.tone-care { color: var(--bud-color-sage); background: var(--bud-color-sage-soft); }
.calendar-legend { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 12px 10px; border-top: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 10px; }
.legend-item { display: inline-flex; align-items: center; gap: 4px; }
.legend-dot { width: 7px; height: 7px; border: 2px solid currentColor; border-radius: 50%; }
.month-count { margin-left: auto; }
.agenda-heading { margin-top: 24px; }
.agenda-list { display: grid; gap: 9px; }
.agenda-item { display: flex; width: 100%; align-items: center; gap: 12px; margin: 0; padding: 14px 12px; line-height: 1.5; text-align: left; }
.agenda-time { color: var(--bud-color-muted); font-size: 13px; font-variant-numeric: tabular-nums; }
.agenda-body { min-width: 0; flex: 1; }
.agenda-title { display: block; color: var(--bud-color-ink); font-size: 15px; font-weight: 650; overflow-wrap: anywhere; }
.agenda-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 5px; color: var(--bud-color-muted); font-size: 12px; }
.agenda-tag { padding: 2px 6px; border-radius: 4px; font-size: 11px; }
@media (min-width: 760px) {
  .calendar-day { min-height: 120px; padding: 5px; }
  .day-select { flex-direction: row; justify-content: space-between; }
  .day-item { font-size: 12px; }
}
</style>
