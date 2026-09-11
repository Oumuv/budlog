<script setup lang="ts">
import { ChartLine, Milk, PackagePlus, RefreshCw, Scale, TrendingUp } from "lucide-vue-next";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { FeedingRecord, MilkStorageRecord, WeightRecord } from "../../types";
import { shiftDay, todayKey, toLocalInput } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

type RangeDays = 7 | 14 | 30;
interface MilkDay { date: string; amount: number; bottleCount: number; directMinutes: number }
interface StorageDay { date: string; amount: number; count: number }
interface WeightDay { date: string; weight?: number }

const rangeDays = ref<RangeDays>(7);
const loading = ref(true);
const error = ref("");
const feedings = ref<FeedingRecord[]>([]);
const milkStorages = ref<MilkStorageRecord[]>([]);
const weights = ref<WeightRecord[]>([]);
const rangeOptions: RangeDays[] = [7, 14, 30];

const dateKeys = computed(() => {
  const today = todayKey();
  return Array.from({ length: rangeDays.value }, (_, index) => shiftDay(today, index - rangeDays.value + 1));
});

const milkDays = computed<MilkDay[]>(() => {
  const map = new Map<string, MilkDay>();
  dateKeys.value.forEach((date) => map.set(date, { date, amount: 0, bottleCount: 0, directMinutes: 0 }));
  feedings.value.forEach((record) => {
    const date = toLocalInput(record.startTime).slice(0, 10);
    const day = map.get(date);
    if (!day) return;
    if (record.amountMl) {
      day.amount += record.amountMl;
      day.bottleCount += 1;
    }
    if (record.feedingType === "BREAST_DIRECT" && record.durationMinutes) day.directMinutes += record.durationMinutes;
  });
  return dateKeys.value.map((date) => map.get(date)!);
});

const storageDays = computed<StorageDay[]>(() => {
  const map = new Map<string, StorageDay>();
  dateKeys.value.forEach((date) => map.set(date, { date, amount: 0, count: 0 }));
  milkStorages.value.forEach((record) => {
    const date = toLocalInput(record.storedAt).slice(0, 10);
    const day = map.get(date);
    if (!day) return;
    day.amount += record.amountMl;
    day.count += 1;
  });
  return dateKeys.value.map((date) => map.get(date)!);
});

const weightDays = computed<WeightDay[]>(() => {
  const byDate = new Map<string, WeightRecord>();
  weights.value.forEach((record) => {
    const date = record.recordDate || toLocalInput(record.measuredAt).slice(0, 10);
    const previous = byDate.get(date);
    if (!previous || new Date(record.measuredAt).getTime() > new Date(previous.measuredAt).getTime()) byDate.set(date, record);
  });
  return dateKeys.value.map((date) => ({ date, weight: byDate.get(date)?.weightKg }));
});

const totalMilk = computed(() => milkDays.value.reduce((sum, day) => sum + day.amount, 0));
const avgMilk = computed(() => Math.round(totalMilk.value / Math.max(1, milkDays.value.length)));
const totalBottleCount = computed(() => milkDays.value.reduce((sum, day) => sum + day.bottleCount, 0));
const totalDirectMinutes = computed(() => milkDays.value.reduce((sum, day) => sum + day.directMinutes, 0));
const actualMaxMilk = computed(() => Math.max(0, ...milkDays.value.map((day) => day.amount)));
const milkScaleMax = computed(() => Math.max(1, actualMaxMilk.value));

const totalStoredMilk = computed(() => storageDays.value.reduce((sum, day) => sum + day.amount, 0));
const avgStoredMilk = computed(() => Math.round(totalStoredMilk.value / Math.max(1, storageDays.value.length)));
const totalStorageCount = computed(() => storageDays.value.reduce((sum, day) => sum + day.count, 0));
const actualMaxStoredMilk = computed(() => Math.max(0, ...storageDays.value.map((day) => day.amount)));
const storageScaleMax = computed(() => Math.max(1, actualMaxStoredMilk.value));

const measuredWeights = computed(() => weightDays.value.filter((day) => day.weight !== undefined) as Array<Required<WeightDay>>);
const latestWeight = computed(() => measuredWeights.value[measuredWeights.value.length - 1]?.weight);
const weightDelta = computed(() => {
  if (measuredWeights.value.length < 2) return undefined;
  return Number((measuredWeights.value[measuredWeights.value.length - 1].weight - measuredWeights.value[0].weight).toFixed(3));
});
const minWeight = computed(() => measuredWeights.value.length ? Math.min(...measuredWeights.value.map((day) => day.weight)) : 0);
const maxWeight = computed(() => measuredWeights.value.length ? Math.max(...measuredWeights.value.map((day) => day.weight)) : 0);

onShow(async () => {
  if (!(await ensureAccess())) return;
  await load();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const today = todayKey();
    const from = shiftDay(today, -(rangeDays.value - 1));
    const to = shiftDay(today, 1);
    const feedingRecords: FeedingRecord[] = [];
    const storageRecords: MilkStorageRecord[] = [];
    const weightRecords: WeightRecord[] = [];

    for (let cursor = from; cursor < to; cursor = shiftDay(cursor, 3)) {
      const candidateTo = shiftDay(cursor, 3);
      const chunkTo = candidateTo < to ? candidateTo : to;
      const [feedingPage, storagePage, weightPage] = await Promise.all([
        api.feedings(cursor, chunkTo),
        api.milkStorages(cursor, chunkTo),
        api.weightRecords(cursor, chunkTo),
      ]);
      feedingRecords.push(...feedingPage.content);
      storageRecords.push(...storagePage.content);
      weightRecords.push(...weightPage.content);
    }

    feedings.value = feedingRecords;
    milkStorages.value = storageRecords;
    weights.value = weightRecords;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "趋势数据加载失败";
  } finally {
    loading.value = false;
  }
}

async function changeRange(days: RangeDays) {
  if (rangeDays.value === days || loading.value) return;
  rangeDays.value = days;
  await load();
}

function milkBarHeight(amount: number) {
  return Math.max(amount ? 8 : 2, Math.round((amount / milkScaleMax.value) * 100));
}

function storageBarHeight(amount: number) {
  return Math.max(amount ? 8 : 2, Math.round((amount / storageScaleMax.value) * 100));
}

function weightBarHeight(weight?: number) {
  if (weight === undefined) return 2;
  if (maxWeight.value === minWeight.value) return 56;
  return 18 + Math.round(((weight - minWeight.value) / (maxWeight.value - minWeight.value)) * 72);
}

function shortDate(date: string) { return date.slice(5).replace("-", "/"); }
function signedWeight(value?: number) {
  if (value === undefined) return "--";
  return `${value > 0 ? "+" : ""}${value.toFixed(3)} kg`;
}
</script>

<template>
  <AppPage>
    <view class="page-shell analytics-page">
    <view class="analytics-head">
      <view>
        <view class="analytics-head__eyebrow"><ChartLine :size="15" /><text>成长观测</text></view>
        <text class="analytics-head__title">宝宝数据大屏</text>
        <text class="analytics-head__sub">奶量、存奶、亲喂与体重趋势一页掌握</text>
      </view>
      <button class="icon-btn" aria-label="刷新" title="刷新" @click="load"><RefreshCw :size="20" /></button>
    </view>

    <view class="range-switch surface">
      <button
        v-for="days in rangeOptions"
        :key="days"
        class="range-switch__item"
        :class="{ 'range-switch__item--active': rangeDays === days }"
        @click="changeRange(days)"
      >近 {{ days }} 天</button>
    </view>

    <AppLoading v-if="loading" copy="正在汇总成长数据" />
    <ErrorState v-else-if="error" title="趋势暂时无法加载" :copy="error" @retry="load" />

    <template v-else>
      <view class="metric-grid">
        <view class="metric-card surface">
          <view class="metric-card__icon"><Milk :size="19" /></view>
          <text class="metric-card__label">累计瓶喂</text>
          <text class="metric-card__value">{{ totalMilk }} <text class="metric-card__unit">ml</text></text>
          <text class="metric-card__meta">{{ totalBottleCount }} 次 · 日均 {{ avgMilk }} ml</text>
        </view>
        <view class="metric-card surface">
          <view class="metric-card__icon metric-card__icon--weight"><Scale :size="19" /></view>
          <text class="metric-card__label">最新体重</text>
          <text class="metric-card__value">{{ latestWeight ?? '--' }} <text class="metric-card__unit">kg</text></text>
          <text class="metric-card__meta">区间变化 {{ signedWeight(weightDelta) }}</text>
        </view>
      </view>

      <view class="chart-grid">
        <view class="chart-card surface">
        <view class="chart-card__head">
          <view>
            <text class="chart-card__title">每日瓶喂奶量</text>
            <text class="chart-card__hint">仅统计有毫升数的瓶喂；亲喂无法换算为奶量</text>
          </view>
          <text class="chart-card__total">峰值 {{ actualMaxMilk }} ml</text>
        </view>
        <view class="bar-chart">
          <view v-for="day in milkDays" :key="day.date" class="bar-chart__column" :title="`${day.date}：${day.amount} ml`" :aria-label="`${day.date} 瓶喂 ${day.amount} ml`">
            <view class="bar-chart__plot">
              <text v-if="day.amount" class="bar-chart__value">{{ day.amount }}</text>
              <view class="bar-chart__bar" :class="{ 'bar-chart__bar--empty': !day.amount }" :style="{ height: `${milkBarHeight(day.amount)}%` }" />
            </view>
            <text class="bar-chart__date">{{ shortDate(day.date) }}</text>
          </view>
        </view>
        <view class="chart-footnote"><Milk :size="14" /><text>同期亲喂 {{ totalDirectMinutes }} 分钟</text></view>
        </view>

        <view class="chart-card surface">
        <view class="chart-card__head">
          <view>
            <text class="chart-card__title">每日存奶量</text>
            <text class="chart-card__hint">按存奶时间汇总每天保存的母乳量</text>
          </view>
          <text class="chart-card__total">累计 {{ totalStoredMilk }} ml</text>
        </view>
        <view class="bar-chart">
          <view v-for="day in storageDays" :key="day.date" class="bar-chart__column" :title="`${day.date}：${day.amount} ml`" :aria-label="`${day.date} 存奶 ${day.amount} ml`">
            <view class="bar-chart__plot">
              <text v-if="day.amount" class="bar-chart__value">{{ day.amount }}</text>
              <view class="bar-chart__bar bar-chart__bar--storage" :class="{ 'bar-chart__bar--empty': !day.amount }" :style="{ height: `${storageBarHeight(day.amount)}%` }" />
            </view>
            <text class="bar-chart__date">{{ shortDate(day.date) }}</text>
          </view>
        </view>
        <view class="chart-footnote"><PackagePlus :size="14" /><text>{{ totalStorageCount }} 次存奶 · 日均 {{ avgStoredMilk }} ml · 峰值 {{ actualMaxStoredMilk }} ml</text></view>
        </view>

        <view class="chart-card surface chart-card--weight">
        <view class="chart-card__head">
          <view>
            <text class="chart-card__title">体重增长趋势</text>
            <text class="chart-card__hint">每天取当日最后一次体重记录</text>
          </view>
          <view class="trend-badge"><TrendingUp :size="14" /><text>{{ signedWeight(weightDelta) }}</text></view>
        </view>
        <view v-if="measuredWeights.length" class="weight-chart">
          <view class="weight-chart__scale">
            <text>{{ maxWeight.toFixed(3) }}</text>
            <text>{{ minWeight.toFixed(3) }}</text>
          </view>
          <view class="weight-chart__body">
            <view v-for="day in weightDays" :key="day.date" class="weight-chart__column" :title="`${day.date}：${day.weight ?? '无记录'}${day.weight === undefined ? '' : ' kg'}`" :aria-label="`${day.date} 体重 ${day.weight ?? '无记录'}`">
              <view class="weight-chart__plot">
                <text v-if="day.weight !== undefined" class="weight-chart__value">{{ day.weight.toFixed(2) }}</text>
                <view class="weight-chart__bar" :class="{ 'weight-chart__bar--empty': day.weight === undefined }" :style="{ height: `${weightBarHeight(day.weight)}%` }" />
              </view>
              <text class="weight-chart__date">{{ shortDate(day.date) }}</text>
            </view>
          </view>
        </view>
        <view v-else class="chart-empty"><Scale :size="20" /><text>这个时间段还没有体重记录</text></view>
        </view>
      </view>
    </template>

    <AppNav current="analytics" />
    </view>
  </AppPage>
</template>

<style scoped>
.analytics-page { padding-bottom: calc(88px + env(safe-area-inset-bottom)); }
.analytics-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.analytics-head__eyebrow { display: flex; align-items: center; gap: 5px; color: var(--bud-color-primary); font-size: 12px; font-weight: 700; }
.analytics-head__title, .analytics-head__sub { display: block; }
.analytics-head__title { margin-top: 3px; font-size: 24px; line-height: 31px; font-weight: 800; }
.analytics-head__sub { margin-top: 3px; color: var(--bud-color-muted); font-size: 12px; line-height: 18px; }
.range-switch { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; margin-bottom: 14px; padding: 4px; }
.range-switch__item { height: 36px; margin: 0; padding: 0; border: 0; border-radius: 7px; color: var(--bud-color-muted); background: transparent; font-size: 12px; line-height: 36px; }
.range-switch__item::after { border: 0; }
.range-switch__item--active { color: var(--bud-color-primary); background: #fff0f2; font-weight: 750; }
.state-panel__icon--error { color: #a33e43; background: var(--bud-color-coral-soft); }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.metric-card { min-width: 0; padding: 15px; }
.metric-card__icon { display: flex; width: 34px; height: 34px; align-items: center; justify-content: center; margin-bottom: 12px; border-radius: 8px; color: var(--baby-blue); background: var(--baby-blue-soft); }
.metric-card__icon--weight { color: var(--baby-purple); background: var(--baby-purple-soft); }
.metric-card__label, .metric-card__value, .metric-card__meta { display: block; }
.metric-card__label { color: var(--bud-color-muted); font-size: 11px; }
.metric-card__value { margin-top: 3px; overflow: hidden; font-size: 23px; line-height: 30px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.metric-card__unit { color: var(--bud-color-muted); font-size: 11px; font-weight: 650; }
.metric-card__meta { margin-top: 5px; color: var(--bud-color-muted); font-size: 10px; line-height: 15px; }
.chart-card { margin-bottom: 12px; padding: 16px 14px 13px; }
.chart-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.chart-card__title, .chart-card__hint { display: block; }
.chart-card__title { font-size: 15px; font-weight: 780; }
.chart-card__hint { max-width: 250px; margin-top: 3px; color: var(--bud-color-muted); font-size: 10px; line-height: 15px; }
.chart-card__total { flex: 0 0 auto; color: var(--bud-color-primary); font-size: 10px; font-weight: 700; }
.bar-chart { display: flex; height: 190px; align-items: stretch; gap: 5px; overflow-x: auto; padding: 6px 1px 0; }
.bar-chart__column { display: flex; min-width: 34px; flex: 1 0 34px; flex-direction: column; align-items: center; }
.bar-chart__plot { position: relative; display: flex; width: 100%; height: 154px; align-items: flex-end; justify-content: center; border-bottom: 1px solid var(--bud-color-line); }
.bar-chart__plot::before, .bar-chart__plot::after { position: absolute; right: 0; left: 0; border-top: 1px dashed var(--bud-color-line-soft); content: ""; }
.bar-chart__plot::before { top: 33%; }
.bar-chart__plot::after { top: 66%; }
.bar-chart__bar { position: relative; z-index: 1; width: 70%; min-height: 3px; border-radius: 7px 7px 2px 2px; background: var(--baby-blue); }
.bar-chart__bar--storage { background: var(--baby-cyan); }
.bar-chart__bar--empty { background: var(--bud-color-line); }
.bar-chart__value { position: absolute; z-index: 2; top: 3px; color: var(--bud-color-body); font-size: 10px; }
.bar-chart__date, .weight-chart__date { margin-top: 6px; color: var(--bud-color-muted); font-size: 10px; white-space: nowrap; }
.chart-footnote { display: flex; align-items: center; gap: 5px; margin-top: 8px; color: var(--bud-color-muted); font-size: 10px; }
.trend-badge { display: flex; flex: 0 0 auto; align-items: center; gap: 4px; padding: 5px 7px; border-radius: 999px; color: var(--baby-purple); background: var(--baby-purple-soft); font-size: 10px; font-weight: 700; }
.weight-chart { display: flex; height: 190px; gap: 7px; }
.weight-chart__scale { display: flex; width: 36px; flex: 0 0 36px; flex-direction: column; justify-content: space-between; padding: 12px 0 25px; color: var(--bud-color-muted); font-size: 8px; text-align: right; }
.weight-chart__body { display: flex; min-width: 0; flex: 1; gap: 5px; overflow-x: auto; }
.weight-chart__column { display: flex; min-width: 34px; flex: 1 0 34px; flex-direction: column; align-items: center; }
.weight-chart__plot { position: relative; display: flex; width: 100%; height: 154px; align-items: flex-end; justify-content: center; border-bottom: 1px solid var(--bud-color-line); background: repeating-linear-gradient(to bottom, transparent 0, transparent 49px, #f4ecec 50px); }
.weight-chart__bar { width: 42%; min-height: 3px; border-radius: 8px 8px 2px 2px; background: var(--baby-purple); }
.weight-chart__bar--empty { height: 2px !important; background: var(--bud-color-line); }
.weight-chart__value { position: absolute; top: 3px; color: var(--baby-purple); font-size: 10px; font-weight: 700; }
.chart-empty { display: flex; min-height: 150px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--bud-color-muted); font-size: 12px; }
@media (max-width: 380px) { .metric-grid { grid-template-columns: 1fr; } }
@media (min-width: 960px) {
  .analytics-page { width: min(1180px, calc(100% - 48px)); }
  .metric-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
  .chart-card { margin-bottom: 0; }
  .chart-card--weight { grid-column: 1 / -1; }
}
</style>
