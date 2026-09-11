<script setup lang="ts">
import { ChevronLeft, ChevronRight, Droplets, Milk, NotebookPen, PackagePlus, Plus, Scale } from "lucide-vue-next";
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

const date = ref(todayKey());
const timeline = ref<Timeline>();
const loading = ref(false);
const error = ref("");
const calendarMinDateKey = ref(`${new Date().getFullYear() - 3}-01-01`);
const calendarMinDate = computed(() => new Date(toIso(`${calendarMinDateKey.value}T00:00`)).getTime());
const calendarMaxDate = Date.now();
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
    if (baby) {
      const birthDate = toLocalInput(baby.birthTime).slice(0, 10);
      calendarMinDateKey.value = birthDate;
    }
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

function go(url: string) {
  uni.navigateTo({ url });
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
      <view class="records-add">
        <button class="icon-btn records-add__feeding" aria-label="新增喂奶" title="新增喂奶" @click="go('/pages/feeding/index?mode=bottle')"><Milk :size="20" /></button>
        <button class="icon-btn records-add__storage" aria-label="新增存奶" title="新增存奶" @click="go('/pages/milk-storage/index')"><PackagePlus :size="20" /></button>
        <button class="icon-btn records-add__diaper" aria-label="新增尿便" title="新增尿便" @click="go('/pages/diaper/index')"><Droplets :size="20" /></button>
      </view>
    </view>

    <view class="date-switch surface">
      <button class="icon-btn" aria-label="前一天" title="前一天" :disabled="date <= calendarMinDateKey" @click="moveDay(-1)"><ChevronLeft :size="21" /></button>
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
      <button class="icon-btn" aria-label="后一天" title="后一天" :disabled="date >= todayKey()" @click="moveDay(1)"><ChevronRight :size="21" /></button>
    </view>

    <view v-if="timeline" class="record-summary">
      <view class="record-summary__metric"><text>{{ timeline.summary.feedingCount }}</text><text>喂奶</text></view>
      <view class="record-summary__metric"><text>{{ timeline.summary.bottleAmountMl }}</text><text>毫升</text></view>
      <view class="record-summary__metric"><text>{{ timeline.summary.directFeedingMinutes }}</text><text>亲喂分钟</text></view>
      <view class="record-summary__metric"><text>{{ timeline.summary.peeCount }}/{{ timeline.summary.poopCount }}</text><text>尿/便</text></view>
      <view class="record-summary__storage">
        <PackagePlus :size="16" />
        <text>存奶 {{ timeline.summary.milkStorageCount }} 次</text>
        <text class="record-summary__storage-amount">{{ timeline.summary.storedMilkAmountMl }} ml</text>
      </view>
      <view class="record-summary__extra">
        <view><Scale :size="16" /><text>体重</text><text class="record-summary__extra-value">{{ timeline.summary.weightKg ?? "--" }} kg</text></view>
        <view><NotebookPen :size="16" /><text>事件</text><text class="record-summary__extra-value">{{ timeline.summary.eventCount }} 条</text></view>
      </view>
    </view>

    <view class="section-title timeline-title">
      <text class="section-title__text">时间线</text>
      <button class="records-backfill" @click="go('/pages/feeding/index')"><Plus :size="16" />补录</button>
    </view>

    <AppLoading v-if="loading" copy="正在读取当天记录" />
    <ErrorState v-else-if="error" title="记录加载失败" :copy="error" @retry="load" />
    <TimelineList v-else :items="timeline?.items || []" editable @edit="edit" />

    <AppNav current="records" />
    </view>
  </AppPage>
</template>

<style scoped>
.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 20px;
}

.records-add {
  display: flex;
  gap: 4px;
}

.records-add .icon-btn {
  border: 1px solid var(--bud-color-line);
  background: #ffffff;
  box-shadow: var(--bud-shadow-sm);
}

.records-add__feeding {
  color: var(--bud-color-coral);
}

.records-add__storage {
  color: var(--bud-color-sage);
}

.records-add__diaper {
  color: var(--bud-color-cyan);
}

.date-switch {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  min-height: 56px;
  padding: 5px 6px;
}

.date-switch__value {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.date-switch__value > svg {
  flex: 0 0 auto;
  color: var(--bud-color-primary);
}

.date-switch__date {
  color: var(--bud-color-ink);
}

.date-switch__today {
  padding: 2px 6px;
  border-radius: 9px;
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-primary-soft);
  font-size: 11px;
}

.date-switch__calendar {
  width: 100%;
}

.record-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 12px;
  padding: 14px 0 0;
  border-bottom: 1px solid var(--bud-color-line);
}

.record-summary__metric {
  min-width: 0;
  border-right: 1px solid var(--bud-color-line);
  text-align: center;
}

.record-summary__metric:nth-child(4) { border-right: 0; }
.record-summary__metric text { display: block; color: var(--bud-color-muted); font-size: 10px; }
.record-summary__metric text:first-child { margin-bottom: 2px; color: var(--bud-color-ink); font-size: 19px; font-weight: 780; }

.record-summary__storage {
  display: flex;
  grid-column: 1 / -1;
  min-width: 0;
  min-height: 40px;
  margin-top: 12px;
  padding: 8px 4px;
  align-items: center;
  gap: 7px;
  border-top: 1px solid var(--bud-color-line-soft);
  color: #47705e;
  font-size: 12px;
  font-weight: 650;
}

.record-summary__storage > svg { flex: 0 0 auto; }
.record-summary__storage-amount { margin-left: auto; color: var(--bud-color-ink); font-weight: 750; }

.record-summary__extra {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--bud-color-line-soft);
}

.record-summary__extra > view {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  min-height: 42px;
  padding: 8px 4px;
  align-items: center;
  gap: 5px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.record-summary__extra > view:first-child {
  border-right: 1px solid var(--bud-color-line-soft);
}

.record-summary__extra-value {
  color: var(--bud-color-ink);
  font-weight: 750;
}

@media (max-width: 420px) {
  .records-head {
    align-items: flex-start;
  }

  .records-add {
    max-width: 140px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

.timeline-title {
  margin-top: 24px;
}

.records-backfill {
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

.records-error {
  color: #a33e43;
  background: var(--bud-color-coral-soft);
}
</style>
