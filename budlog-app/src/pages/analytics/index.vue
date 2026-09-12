<script setup lang="ts">
import { Milk, PackagePlus, RefreshCw, Scale } from "lucide-vue-next";
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
const chartDayWidth = 46;

const dateKeys = computed(() => {
  const today = todayKey();
  return Array.from({ length: rangeDays.value }, (_, index) => shiftDay(today, index - rangeDays.value + 1));
});
const chartWidth = computed(() => rangeDays.value === 7 ? "100%" : `${rangeDays.value * chartDayWidth}px`);

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
const weightPoints = computed(() => {
  const span = maxWeight.value - minWeight.value;
  return weightDays.value.flatMap((day, index) => {
    if (day.weight === undefined) return [];
    const x = ((index + 0.5) / weightDays.value.length) * 100;
    const y = span === 0 ? 31 : 44 - ((day.weight - minWeight.value) / span) * 28;
    return [{ date: day.date, weight: day.weight, x, y, yPercent: (y / 58) * 100 }];
  });
});
const weightPolyline = computed(() => weightPoints.value.map((point) => `${point.x},${point.y}`).join(" "));

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
  return Math.max(amount ? 10 : 0, Math.round((amount / milkScaleMax.value) * 82));
}

function storageBarHeight(amount: number) {
  return Math.max(amount ? 10 : 0, Math.round((amount / storageScaleMax.value) * 82));
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
          <text class="page-title">宝宝数据大屏</text>
          <text class="page-subtitle">奶量、存奶、体重与关键趋势一页掌握</text>
        </view>
        <button class="icon-btn refresh-button" aria-label="刷新" title="刷新" @click="load"><RefreshCw :size="19" /></button>
      </view>

      <view class="range-switch surface">
        <button v-for="days in rangeOptions" :key="days" class="range-switch__item" :class="{ 'range-switch__item--active': rangeDays === days }" @click="changeRange(days)">近 {{ days }} 天</button>
      </view>

      <AppLoading v-if="loading" copy="正在汇总成长数据" />
      <ErrorState v-else-if="error" title="趋势暂时无法加载" :copy="error" @retry="load" />

      <template v-else>
        <view class="metric-grid">
          <view class="metric-card surface">
            <view class="metric-card__label"><view class="metric-card__icon"><Milk :size="18" /></view><text>累计瓶喂</text></view>
            <text class="metric-card__value">{{ totalMilk }} <small>ml</small></text>
            <text class="metric-card__meta">{{ totalBottleCount }} 次 · 日均 {{ avgMilk }} ml</text>
          </view>
          <view class="metric-card surface">
            <view class="metric-card__label"><view class="metric-card__icon metric-card__icon--weight"><Scale :size="18" /></view><text>最新体重</text></view>
            <text class="metric-card__value">{{ latestWeight ?? "--" }} <small>kg</small></text>
            <text class="metric-card__meta">较区间首日 {{ signedWeight(weightDelta) }}</text>
          </view>
        </view>

        <view class="chart-card surface">
          <view class="chart-card__head"><text>每日瓶喂奶量</text><small>单位：ml</small></view>
          <scroll-view class="chart-scroll" scroll-x>
            <view class="bar-chart" :style="{ width: chartWidth }">
              <view v-for="day in milkDays" :key="day.date" class="bar-chart__column" :title="`${day.date}：${day.amount} ml`">
                <view class="bar-chart__plot">
                  <text class="bar-chart__value" :style="{ bottom: `calc(${milkBarHeight(day.amount)}% + 4px)` }">{{ day.amount }}</text>
                  <view class="bar-chart__bar" :style="{ height: `${milkBarHeight(day.amount)}%` }" />
                </view>
                <text class="chart-date">{{ shortDate(day.date) }}</text>
              </view>
            </view>
          </scroll-view>
          <view class="chart-footnote"><Milk :size="14" /><text>同期亲喂 {{ totalDirectMinutes }} 分钟 · 日均 {{ avgMilk }} ml</text></view>
        </view>

        <view class="chart-card surface">
          <view class="chart-card__head"><text>每日存奶量</text><small>单位：ml</small></view>
          <scroll-view class="chart-scroll" scroll-x>
            <view class="bar-chart" :style="{ width: chartWidth }">
              <view v-for="day in storageDays" :key="day.date" class="bar-chart__column" :title="`${day.date}：${day.amount} ml`">
                <view class="bar-chart__plot">
                  <text class="bar-chart__value" :style="{ bottom: `calc(${storageBarHeight(day.amount)}% + 4px)` }">{{ day.amount }}</text>
                  <view class="bar-chart__bar bar-chart__bar--storage" :style="{ height: `${storageBarHeight(day.amount)}%` }" />
                </view>
                <text class="chart-date">{{ shortDate(day.date) }}</text>
              </view>
            </view>
          </scroll-view>
          <view class="chart-footnote"><PackagePlus :size="14" /><text>{{ totalStorageCount }} 次存奶 · 日均 {{ avgStoredMilk }} ml · 峰值 {{ actualMaxStoredMilk }} ml</text></view>
        </view>

        <view class="chart-card surface chart-card--weight">
          <view class="chart-card__head"><text>体重增长趋势</text><small>单位：kg</small></view>
          <scroll-view v-if="measuredWeights.length" class="chart-scroll" scroll-x>
            <view class="weight-chart" :style="{ width: chartWidth }">
              <view class="weight-chart__plot">
                <svg viewBox="0 0 100 58" preserveAspectRatio="none" aria-hidden="true">
                  <line x1="0" y1="18" x2="100" y2="18" />
                  <line x1="0" y1="32" x2="100" y2="32" />
                  <line x1="0" y1="46" x2="100" y2="46" />
                  <polyline :points="weightPolyline" />
                </svg>
                <view v-for="point in weightPoints" :key="`marker-${point.date}`" class="weight-chart__marker" :style="{ left: `${point.x}%`, top: `${point.yPercent}%` }" />
                <text v-for="point in weightPoints" :key="`value-${point.date}`" class="weight-chart__value" :style="{ left: `${point.x}%`, top: `${point.yPercent}%` }">{{ point.weight.toFixed(2) }}</text>
              </view>
              <view class="weight-chart__dates" :style="{ gridTemplateColumns: `repeat(${rangeDays}, minmax(42px, 1fr))` }"><text v-for="day in weightDays" :key="day.date">{{ shortDate(day.date) }}</text></view>
            </view>
          </scroll-view>
          <view v-else class="chart-empty"><Scale :size="21" /><text>这个时间段还没有体重记录</text></view>
        </view>
      </template>

      <AppNav current="analytics" />
    </view>
  </AppPage>
</template>

<style scoped>
.analytics-page { padding-top: max(22px, env(safe-area-inset-top)); }
.analytics-head { display: flex; min-height: 54px; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; padding: 0 5px; }
.refresh-button { border: 1px solid var(--bud-color-line); background: #fff; }
.range-switch { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px; margin-bottom: 12px; padding: 4px; }
.range-switch__item { height: 35px; margin: 0; padding: 0; border: 0; border-radius: 7px; color: var(--bud-color-muted); background: transparent; font-size: 11px; line-height: 35px; }
.range-switch__item--active { color: #fff; background: var(--bud-color-primary); box-shadow: 0 5px 12px rgba(255, 79, 135, 0.2); font-weight: 750; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.metric-card { min-width: 0; padding: 13px 12px; }
.metric-card__label { display: flex; align-items: center; gap: 8px; color: var(--bud-color-body); font-size: 11px; font-weight: 700; }
.metric-card__icon { display: flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 7px; color: var(--baby-blue); background: var(--baby-blue-soft); }
.metric-card__icon--weight { color: var(--baby-purple); background: var(--baby-purple-soft); }
.metric-card__value, .metric-card__meta { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.metric-card__value { margin-top: 7px; font-size: 22px; line-height: 28px; font-weight: 850; }
.metric-card__value small { color: var(--bud-color-body); font-size: 10px; font-weight: 700; }
.metric-card__meta { margin-top: 3px; color: var(--bud-color-muted); font-size: 9px; }
.chart-card { margin-top: 11px; padding: 13px 12px 10px; }
.chart-card__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; font-size: 14px; font-weight: 800; }
.chart-card__head small { color: var(--bud-color-primary); font-size: 9px; font-weight: 600; }
.chart-scroll { width: 100%; }
.bar-chart { display: flex; min-width: 100%; height: 154px; align-items: stretch; }
.bar-chart__column { display: flex; min-width: 42px; flex: 1 1 0; flex-direction: column; align-items: center; }
.bar-chart__plot { position: relative; display: flex; width: 100%; height: 128px; align-items: flex-end; justify-content: center; border-bottom: 1px solid var(--bud-color-line); background: repeating-linear-gradient(to bottom, transparent 0, transparent 41px, #f0f3f7 42px); }
.bar-chart__bar { width: 60%; max-width: 28px; min-height: 1px; border-radius: 6px 6px 1px 1px; background: #f46b98; }
.bar-chart__bar--storage { background: #45c996; }
.bar-chart__value { position: absolute; z-index: 1; color: #52617c; font-size: 9px; line-height: 12px; transform: translateY(1px); }
.chart-date { margin-top: 5px; color: var(--bud-color-muted); font-size: 9px; white-space: nowrap; }
.chart-footnote { display: flex; min-height: 25px; align-items: center; gap: 5px; margin-top: 4px; padding-top: 6px; border-top: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 9px; }
.weight-chart { min-width: 100%; }
.weight-chart__plot { position: relative; height: 136px; }
.weight-chart__plot svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.weight-chart__plot line { stroke: #edf0f5; stroke-width: 0.55; }
.weight-chart__plot polyline { fill: none; stroke: var(--baby-purple); stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.4; vector-effect: non-scaling-stroke; }
.weight-chart__marker { position: absolute; z-index: 1; width: 7px; height: 7px; border: 2px solid #fff; border-radius: 50%; background: var(--baby-purple); box-sizing: border-box; transform: translate(-50%, -50%); }
.weight-chart__value { position: absolute; color: var(--baby-purple); font-size: 8px; font-weight: 750; transform: translate(-50%, -17px); }
.weight-chart__dates { display: grid; color: var(--bud-color-muted); font-size: 9px; text-align: center; }
.chart-empty { display: flex; min-height: 135px; flex-direction: column; align-items: center; justify-content: center; gap: 7px; color: var(--bud-color-muted); font-size: 11px; }
@media (max-width: 350px) {
  .metric-card__value { font-size: 19px; }
}
</style>
