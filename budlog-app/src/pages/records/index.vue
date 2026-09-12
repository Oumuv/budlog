<script setup lang="ts">
import {
  Baby,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Droplets,
  ListFilter,
  Milk,
  NotebookPen,
  PackagePlus,
  Plus,
  Scale,
  Timer,
} from "lucide-vue-next";
import { NDatePicker } from "naive-ui";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import TimelineList from "../../components/TimelineList.vue";
import type { Timeline, TimelineItem } from "../../types";
import { shiftDay, todayKey, toIso, toLocalInput } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

type RecordFilter = "ALL" | "FEEDING" | "DIAPER" | "WEIGHT" | "EVENT";

const date = ref(todayKey());
const timeline = ref<Timeline>();
const filter = ref<RecordFilter>("ALL");
const loading = ref(false);
const error = ref("");
const calendarMinDateKey = ref(`${new Date().getFullYear() - 3}-01-01`);
const calendarMinDate = computed(() => new Date(toIso(`${calendarMinDateKey.value}T00:00`)).getTime());
const calendarMaxDate = Date.now();
const filterOptions: Array<{ value: RecordFilter; label: string; icon: typeof ListFilter }> = [
  { value: "ALL", label: "全部", icon: ListFilter },
  { value: "FEEDING", label: "喂奶", icon: Milk },
  { value: "DIAPER", label: "尿便", icon: Droplets },
  { value: "WEIGHT", label: "体重", icon: Scale },
  { value: "EVENT", label: "事件", icon: NotebookPen },
];
const createItems = ["喂奶", "存奶", "尿便", "体重", "事件"];
const createUrls = [
  "/pages/feeding/index",
  "/pages/milk-storage/index",
  "/pages/diaper/index",
  "/pages/weight/index",
  "/pages/event/index",
];

const calendarValue = computed({
  get: () => new Date(toIso(`${date.value}T12:00`)).getTime(),
  set: (value: number | number[] | null) => {
    if (typeof value !== "number") return;
    const nextDate = toLocalInput(new Date(value).toISOString()).slice(0, 10);
    if (nextDate === date.value) return;
    date.value = nextDate;
    void load();
  },
});
const filteredItems = computed(() => {
  const items = timeline.value?.items ?? [];
  return filter.value === "ALL" ? items : items.filter((item) => item.category === filter.value);
});

onShow(async () => {
  if (await ensureAccess()) await load(true);
});

async function load(refreshBaby = false) {
  loading.value = true;
  error.value = "";
  try {
    const [nextTimeline, baby] = await Promise.all([
      api.timeline(date.value),
      refreshBaby ? api.getBaby() : Promise.resolve(undefined),
    ]);
    timeline.value = nextTimeline;
    if (baby) calendarMinDateKey.value = toLocalInput(baby.birthTime).slice(0, 10);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "记录加载失败";
  } finally {
    loading.value = false;
  }
}

async function moveDay(amount: number) {
  const nextDate = shiftDay(date.value, amount);
  if (nextDate < calendarMinDateKey.value || nextDate > todayKey()) return;
  date.value = nextDate;
  await load();
}

function countFor(value: RecordFilter) {
  const items = timeline.value?.items ?? [];
  return value === "ALL" ? items.length : items.filter((item) => item.category === value).length;
}

function go(url: string) {
  uni.navigateTo({ url });
}

function openCreate() {
  uni.showActionSheet({
    itemList: createItems,
    success: ({ tapIndex }) => go(createUrls[tapIndex]),
  });
}

function edit(item: TimelineItem) {
  if (item.category === "FEEDING") go(`/pages/feeding/index?id=${item.id}`);
  if (item.category === "MILK_STORAGE") go(`/pages/milk-storage/index?id=${item.id}`);
  if (item.category === "DIAPER") go(`/pages/diaper/index?id=${item.id}`);
  if (item.category === "WEIGHT") go(`/pages/weight/index?id=${item.id}`);
  if (item.category === "EVENT") go(`/pages/event/index?id=${item.id}`);
}
</script>

<template>
  <AppPage>
    <view class="page-shell records-page">
      <view class="records-head">
        <view>
          <text class="page-title">记录</text>
          <text class="page-subtitle">按日期回看宝宝的日常记录</text>
        </view>
        <button class="add-record" @click="openCreate"><Plus :size="18" />记录</button>
      </view>

      <view class="record-quick-actions" aria-label="常用记录">
        <button class="record-quick-action record-quick-action--bottle" @click="go('/pages/feeding/index?mode=bottle')"><Milk :size="18" /><text>瓶喂</text></button>
        <button class="record-quick-action record-quick-action--diaper" @click="go('/pages/diaper/index')"><Droplets :size="18" /><text>尿便</text></button>
        <button class="record-quick-action record-quick-action--storage" @click="go('/pages/milk-storage/index')"><PackagePlus :size="18" /><text>存奶</text></button>
      </view>

      <view class="date-switch surface">
        <button class="icon-btn" aria-label="前一天" title="前一天" :disabled="date <= calendarMinDateKey" @click="moveDay(-1)"><ChevronLeft :size="20" /></button>
        <NDatePicker
          v-model:value="calendarValue"
          class="date-switch__calendar"
          type="date"
          placeholder="选择记录日期"
          :min-date="calendarMinDate"
          :max-date="calendarMaxDate"
          :clearable="false"
          format="yyyy-MM-dd"
        />
        <button class="icon-btn" aria-label="后一天" title="后一天" :disabled="date >= todayKey()" @click="moveDay(1)"><ChevronRight :size="20" /></button>
      </view>

      <view class="record-filters">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          class="record-filter"
          :class="[`record-filter--${option.value.toLowerCase()}`, { 'record-filter--active': filter === option.value }]"
          :aria-pressed="filter === option.value"
          @click="filter = option.value"
        >
          <component :is="option.icon" :size="18" />
          <text>{{ option.label }}</text>
          <text class="record-filter__count">{{ countFor(option.value) }}</text>
        </button>
      </view>

      <view v-if="timeline" class="daily-card surface">
        <text class="daily-card__title">今日数据</text>
        <view class="daily-card__metrics">
          <view><text>{{ timeline.summary.feedingCount }}</text><text>喂奶</text></view>
          <view><text>{{ timeline.summary.bottleAmountMl }}</text><text>毫升</text></view>
          <view><text>{{ timeline.summary.directFeedingMinutes }}</text><text>亲喂(分钟)</text></view>
          <view><text>{{ timeline.summary.peeCount }}/{{ timeline.summary.poopCount }}</text><text>尿/便</text></view>
        </view>
        <view class="daily-card__details">
          <view><PackagePlus :size="17" /><text>存奶 {{ timeline.summary.milkStorageCount }} 次</text><strong>{{ timeline.summary.storedMilkAmountMl }} ml</strong></view>
          <view><Scale :size="17" /><text>体重</text><strong>{{ timeline.summary.weightKg ?? "--" }} kg</strong></view>
          <view><NotebookPen :size="17" /><text>事件</text><strong>{{ timeline.summary.eventCount }} 条</strong></view>
        </view>
      </view>

      <view class="section-title timeline-title">
        <text class="section-title__text">时间线</text>
        <view class="timeline-title__sort"><CalendarDays :size="14" /><text>{{ date === todayKey() ? "今天" : date.slice(5) }}</text></view>
      </view>

      <AppLoading v-if="loading" copy="正在读取当天记录" />
      <ErrorState v-else-if="error" title="记录加载失败" :copy="error" @retry="load" />
      <TimelineList v-else :items="filteredItems" editable @edit="edit" />

      <AppNav current="records" />
    </view>
  </AppPage>
</template>

<style scoped>
.records-page { padding-top: max(22px, env(safe-area-inset-top)); }
.records-head { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; padding: 0 5px; }
.add-record { display: inline-flex; min-height: 40px; align-items: center; justify-content: center; gap: 5px; margin: 0; padding: 0 14px; border: 0; border-radius: 8px; color: #fff; background: var(--bud-color-primary); box-shadow: 0 7px 16px rgba(255, 79, 135, 0.22); font-size: 13px; font-weight: 750; }
.record-quick-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: 10px; }
.record-quick-action { display: flex; min-width: 0; min-height: 50px; align-items: center; justify-content: center; gap: 7px; margin: 0; padding: 6px 8px; border: 1px solid transparent; border-radius: 8px; font-size: 11px; font-weight: 750; }
.record-quick-action:active { opacity: 0.82; }
.record-quick-action--bottle { color: #267dea; border-color: #dceaff; background: #edf5ff; }
.record-quick-action--diaper { color: #d98000; border-color: #ffebc5; background: #fff7e7; }
.record-quick-action--storage { color: #169c6b; border-color: #d3f2e3; background: #eafbf4; }
.date-switch { display: grid; grid-template-columns: 40px minmax(0, 1fr) 40px; align-items: center; min-height: 52px; padding: 4px; }
.date-switch .icon-btn { width: 36px; height: 36px; }
.date-switch__calendar { width: 100%; }
.date-switch :deep(.n-input) { border-radius: 6px; background: #fafbfe; }
.date-switch :deep(.n-input__border), .date-switch :deep(.n-input__state-border) { border-color: transparent; }
.date-switch :deep(.n-input__input-el) { font-size: 13px; font-weight: 700; }
.record-filters { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 3px; margin-top: 10px; }
.record-filter { display: flex; min-width: 0; min-height: 73px; flex-direction: column; align-items: center; justify-content: center; gap: 3px; margin: 0; padding: 6px 1px; border: 1px solid var(--bud-color-line); border-radius: 8px; color: var(--bud-color-body); background: #fff; font-size: 10px; }
.record-filter .lucide { color: var(--baby-blue); }
.record-filter--feeding .lucide { color: var(--bud-color-primary); }
.record-filter--diaper .lucide { color: var(--baby-orange); }
.record-filter--weight .lucide { color: var(--baby-purple); }
.record-filter--event .lucide { color: var(--baby-cyan); }
.record-filter--active { border-color: #ffd6e3; color: var(--bud-color-primary); background: var(--bud-color-primary-soft); box-shadow: var(--bud-shadow-sm); font-weight: 700; }
.record-filter__count { font-size: 10px; font-weight: 750; }
.daily-card { margin-top: 12px; overflow: hidden; }
.daily-card__title { display: block; padding: 12px 13px 7px; font-size: 14px; font-weight: 800; }
.daily-card__metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 5px 4px 11px; }
.daily-card__metrics > view { min-width: 0; border-right: 1px solid var(--bud-color-line-soft); text-align: center; }
.daily-card__metrics > view:last-child { border-right: 0; }
.daily-card__metrics text { display: block; overflow: hidden; color: var(--bud-color-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.daily-card__metrics text:first-child { margin-bottom: 2px; color: var(--bud-color-ink); font-size: 16px; font-weight: 800; }
.daily-card__details { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-top: 1px solid var(--bud-color-line-soft); }
.daily-card__details > view { display: grid; min-width: 0; grid-template-columns: 20px minmax(0, 1fr); gap: 3px; padding: 9px 7px; border-right: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 9px; }
.daily-card__details > view:last-child { border-right: 0; }
.daily-card__details .lucide { grid-row: 1 / span 2; color: var(--baby-green); }
.daily-card__details > view:nth-child(2) .lucide { color: var(--baby-purple); }
.daily-card__details > view:nth-child(3) .lucide { color: var(--baby-cyan); }
.daily-card__details text, .daily-card__details strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.daily-card__details strong { color: var(--bud-color-ink); font-size: 11px; }
.timeline-title { margin-top: 18px; }
.timeline-title__sort { display: flex; align-items: center; gap: 4px; color: var(--bud-color-muted); font-size: 10px; }
@media (max-width: 350px) {
  .record-filters { gap: 2px; }
  .record-filter { min-height: 68px; }
}
</style>
