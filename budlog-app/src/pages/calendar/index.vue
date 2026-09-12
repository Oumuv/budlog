<script setup lang="ts">
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-vue-next";
import { NButton } from "naive-ui";
import { onShow, onUnload } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { CalendarItem } from "../../types";
import { calendarDetailUrl, calendarGroup, calendarItemLabel, monthGrid, shiftMonth } from "../../utils/calendar";
import { formatTime, setAppTimezone, shiftDay, todayKey } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const today = ref(todayKey());
const selectedDate = ref(today.value);
const month = ref(today.value.slice(0, 7));
const items = ref<CalendarItem[]>([]);
const loading = ref(true);
const error = ref("");
const needsSetup = ref(false);
const ready = ref(false);
let requestId = 0;

const weekdays = ["日", "一", "二", "三", "四", "五", "六"];
const dates = computed(() => monthGrid(month.value));
const monthTitle = computed(() => `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月`);
const byDate = computed(() => {
  const result: Record<string, CalendarItem[]> = {};
  items.value.forEach((item) => (result[item.date] ||= []).push(item));
  return result;
});
const selectedItems = computed(() => byDate.value[selectedDate.value] || []);
const days = computed(() => dates.value.map((date) => {
  const records = byDate.value[date] || [];
  const preview = [...records].sort((a, b) => priority(a) - priority(b)).slice(0, 1);
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
    const result = await api.calendar(grid[0], shiftDay(grid[grid.length - 1], 1));
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

function itemTone(item: CalendarItem) {
  if (item.category === "MILK_STORAGE") return "storage";
  if (item.category === "FEEDING") return "feeding";
  if (item.category === "DIAPER") return "diaper";
  if (item.category === "WEIGHT") return "weight";
  return calendarGroup(item.category).toLowerCase();
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
  uni.redirectTo({ url: "/pages/settings/index" });
}
</script>

<template>
  <AppPage>
    <view class="page-shell calendar-page">
      <view class="calendar-head">
        <view>
          <text class="page-title">育儿日历</text>
          <text class="page-subtitle">每天的安排、成长节点与照护记录</text>
        </view>
        <button class="today-button" :disabled="!ready" @click="goToday">今天</button>
      </view>

      <view v-if="needsSetup" class="state-panel surface setup-state">
        <view class="state-panel__icon"><CalendarDays :size="22" /></view>
        <text class="state-panel__title">先添加宝宝资料</text>
        <NButton type="primary" @click="goSettings">前往设置</NButton>
      </view>
      <template v-else>
        <view class="month-toolbar">
          <button class="icon-btn" aria-label="上个月" :disabled="!ready" @click="changeMonth(shiftMonth(month, -1))"><ChevronLeft :size="20" /></button>
          <picker mode="date" fields="month" :value="`${month}-01`" :disabled="!ready" @change="pickMonth">
            <view class="month-title">{{ monthTitle }}</view>
          </picker>
          <button class="icon-btn" aria-label="下个月" :disabled="!ready" @click="changeMonth(shiftMonth(month, 1))"><ChevronRight :size="20" /></button>
        </view>

        <ErrorState v-if="error" title="日历加载失败" :copy="error" @retry="load" />
        <AppLoading v-else-if="loading" copy="正在整理这个月的事项" />
        <template v-else>
          <view class="calendar surface">
            <view class="calendar-weekdays"><text v-for="weekday in weekdays" :key="weekday">{{ weekday }}</text></view>
            <view class="calendar-grid">
              <view v-for="(day, index) in days" :key="day.date" class="calendar-day" :class="{ 'calendar-day--outside': !day.date.startsWith(month), 'calendar-day--selected': day.date === selectedDate }">
                <button class="day-select" :aria-label="`${day.date}，${day.records.length} 条事项`" :aria-pressed="day.date === selectedDate" @click="selectDay(day.date)">
                  <text class="day-number" :class="{ 'day-number--today': day.date === today, 'day-number--sunday': index % 7 === 0 }">{{ day.number }}</text>
                </button>
                <button v-for="item in day.preview" :key="item.key" class="day-item" :class="`tone-${itemTone(item)}`" :title="item.title" @click="openItem(item)">{{ calendarItemLabel(item).slice(0, 2) }}</button>
              </view>
            </view>
            <view class="calendar-legend">
              <view class="legend-item"><view class="legend-dot legend-dot--task" /><text>任务</text></view>
              <view class="legend-item"><view class="legend-dot legend-dot--milestone" /><text>日程</text></view>
              <view class="legend-item"><view class="legend-dot legend-dot--feeding" /><text>节点</text></view>
              <view class="legend-item"><view class="legend-dot legend-dot--event" /><text>事件</text></view>
              <view class="legend-item"><view class="legend-dot legend-dot--care" /><text>日常</text></view>
            </view>
          </view>

          <view class="section-title agenda-heading"><text class="section-title__text">{{ selectedDate }}{{ selectedDate === today ? ' · 今天' : '' }}</text></view>
          <view v-if="selectedItems.length" class="agenda-list">
            <button v-for="item in selectedItems" :key="item.key" class="agenda-item surface" @click="openItem(item)">
              <text class="agenda-time">{{ formatTime(item.eventTime) }}</text>
              <view class="agenda-body">
                <text class="agenda-title">{{ item.title }}</text>
                <view class="agenda-meta"><text class="agenda-tag" :class="`tone-${itemTone(item)}`">{{ calendarItemLabel(item) }}</text><text v-if="item.subtitle">{{ item.subtitle }}</text></view>
              </view>
              <ChevronRight :size="16" class="muted" />
            </button>
          </view>
          <view v-else class="calendar-empty surface">
            <image src="/static/ui/baby-sleep.png" mode="aspectFit" />
            <text class="calendar-empty__title">这一天暂无事项</text>
            <text class="calendar-empty__copy">可切换日期，查看其他安排与记录</text>
          </view>
        </template>
      </template>

      <AppNav current="tasks" />
    </view>
  </AppPage>
</template>

<style scoped>
.calendar-page { padding-top: max(22px, env(safe-area-inset-top)); }
.calendar-head { display: flex; min-height: 54px; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 13px; padding: 0 5px; }
.today-button { min-height: 32px; margin: 1px 0 0; padding: 0 10px; border: 1px solid var(--bud-color-line); border-radius: 8px; color: var(--bud-color-body); background: #fff; font-size: 10px; line-height: 30px; }
.setup-state { min-height: 330px; }
.month-toolbar { display: grid; grid-template-columns: 38px minmax(0, 1fr) 38px; align-items: center; gap: 6px; margin-bottom: 10px; }
.month-toolbar .icon-btn { border: 1px solid var(--bud-color-line); background: #fff; box-shadow: var(--bud-shadow-sm); }
.month-toolbar picker { min-width: 0; text-align: center; }
.month-title { padding: 8px 0; font-size: 17px; font-weight: 800; }
.calendar { overflow: hidden; }
.calendar-weekdays, .calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); }
.calendar-weekdays { padding: 10px 2px 8px; color: #7e8aa1; text-align: center; font-size: 10px; }
.calendar-weekdays text:first-child { color: var(--bud-color-primary); }
.calendar-weekdays text:last-child { color: var(--baby-blue); }
.calendar-day { position: relative; min-width: 0; min-height: 68px; padding: 4px 3px 5px; border-top: 1px solid var(--bud-color-line-soft); }
.calendar-day:not(:nth-child(7n + 1)) { border-left: 1px solid var(--bud-color-line-soft); }
.calendar-day--outside { background: #fffbf8; }
.calendar-day--outside .day-number { color: #b5bdcb; }
.day-select { display: flex; width: 100%; min-height: 30px; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; background: transparent; }
.day-number { display: inline-flex; width: 27px; height: 27px; align-items: center; justify-content: center; border-radius: 50%; color: var(--bud-color-ink); font-size: 11px; font-weight: 700; }
.day-number--sunday { color: var(--bud-color-primary); }
.day-number--today { color: #fff !important; background: var(--bud-color-primary); box-shadow: 0 4px 9px rgba(255, 79, 135, 0.24); }
.calendar-day--selected:not(.calendar-day--outside) .day-number:not(.day-number--today) { color: var(--bud-color-primary); background: var(--bud-color-primary-soft); }
.day-item { display: block; overflow: hidden; width: 100%; height: 18px; margin: 2px 0 0; padding: 0 2px; border: 0; border-radius: 4px; font-size: 8px; line-height: 18px; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.tone-task { color: #247ae5; background: var(--baby-blue-soft); }
.tone-milestone { color: var(--bud-color-primary-dark); background: var(--bud-color-primary-soft); }
.tone-event { color: #db7e09; background: var(--baby-yellow-soft); }
.tone-care { color: #15966a; background: var(--baby-green-soft); }
.tone-feeding { color: #16889b; background: #e8f9fb; }
.tone-diaper { color: #b87916; background: #fff6df; }
.tone-storage { color: #15966a; background: var(--baby-green-soft); }
.tone-weight { color: #7552d4; background: var(--baby-purple-soft); }
.calendar-legend { display: flex; align-items: center; justify-content: space-evenly; padding: 10px 8px; border-top: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 9px; }
.legend-item { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.legend-dot { display: block; width: 8px; height: 8px; flex: 0 0 8px; border-radius: 2px; }
.legend-dot--task { background: var(--baby-blue); }
.legend-dot--milestone { background: var(--baby-primary); }
.legend-dot--feeding { background: #55bcd0; }
.legend-dot--event { background: var(--baby-orange); }
.legend-dot--care { background: #e8c64b; }
.agenda-heading { margin-top: 16px; }
.agenda-list { display: grid; gap: 7px; }
.agenda-item { display: grid; width: 100%; grid-template-columns: 42px minmax(0, 1fr) 16px; align-items: center; gap: 8px; margin: 0; padding: 11px 10px; text-align: left; }
.agenda-time { color: var(--bud-color-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.agenda-body { min-width: 0; }
.agenda-title { display: block; overflow: hidden; color: var(--bud-color-ink); font-size: 12px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.agenda-meta { display: flex; min-width: 0; align-items: center; gap: 6px; margin-top: 3px; overflow: hidden; color: var(--bud-color-muted); font-size: 9px; white-space: nowrap; }
.agenda-tag { flex: 0 0 auto; padding: 2px 5px; border-radius: 4px; }
.calendar-empty { display: flex; min-height: 170px; flex-direction: column; align-items: center; justify-content: center; padding: 18px; text-align: center; }
.calendar-empty image { width: 154px; height: 92px; }
.calendar-empty__title, .calendar-empty__copy { display: block; }
.calendar-empty__title { margin-top: 2px; font-size: 13px; font-weight: 800; }
.calendar-empty__copy { margin-top: 5px; color: var(--bud-color-muted); font-size: 9px; }
</style>
