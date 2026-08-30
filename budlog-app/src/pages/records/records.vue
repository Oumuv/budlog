<script setup lang="ts">
import { ChevronLeft, ChevronRight, Droplets, Milk, Plus } from "lucide-vue-next";
import { onShow } from "@dcloudio/uni-app";
import { ref } from "vue";
import { api } from "../../api";
import AppNav from "../../components/AppNav.vue";
import TimelineList from "../../components/TimelineList.vue";
import type { Timeline, TimelineItem } from "../../types";
import { shiftDay, todayKey } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const date = ref(todayKey());
const timeline = ref<Timeline>();
const loading = ref(false);
const error = ref("");

onShow(async () => {
  if (await ensureAccess()) await load();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    timeline.value = await api.timeline(date.value);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "记录加载失败";
  } finally {
    loading.value = false;
  }
}

async function moveDay(amount: number) {
  date.value = shiftDay(date.value, amount);
  await load();
}

async function pickDate(event: { detail: { value: string } }) {
  date.value = event.detail.value;
  await load();
}

function go(url: string) {
  uni.navigateTo({ url });
}

function edit(item: TimelineItem) {
  if (item.category === "FEEDING") go(`/pages/feeding/feeding?id=${item.id}`);
  if (item.category === "DIAPER") go(`/pages/diaper/diaper?id=${item.id}`);
}
</script>

<template>
  <view class="page-shell records-page">
    <view class="records-head">
      <text class="page-title">记录</text>
      <view class="records-add">
        <button class="icon-btn" aria-label="新增喂奶" @click="go('/pages/feeding/feeding?mode=bottle')"><Milk :size="20" /></button>
        <button class="icon-btn" aria-label="新增尿便" @click="go('/pages/diaper/diaper')"><Droplets :size="20" /></button>
      </view>
    </view>

    <view class="date-switch surface">
      <button class="icon-btn" aria-label="前一天" @click="moveDay(-1)"><ChevronLeft :size="21" /></button>
      <picker mode="date" :value="date" @change="pickDate">
        <view class="date-switch__value">{{ date }}<text v-if="date === todayKey()">今天</text></view>
      </picker>
      <button class="icon-btn" aria-label="后一天" :disabled="date >= todayKey()" @click="moveDay(1)"><ChevronRight :size="21" /></button>
    </view>

    <view v-if="timeline" class="record-summary">
      <view><text>{{ timeline.summary.feedingCount }}</text><text>喂奶</text></view>
      <view><text>{{ timeline.summary.bottleAmountMl }}</text><text>毫升</text></view>
      <view><text>{{ timeline.summary.directFeedingMinutes }}</text><text>亲喂分钟</text></view>
      <view><text>{{ timeline.summary.peeCount }}/{{ timeline.summary.poopCount }}</text><text>尿/便</text></view>
    </view>

    <view class="section-title timeline-title">
      <text class="section-title__text">时间线</text>
      <button class="btn btn--ghost" @click="go('/pages/feeding/feeding')"><Plus :size="17" />补录</button>
    </view>

    <view v-if="loading" class="surface empty-state">加载中</view>
    <view v-else-if="error" class="surface empty-state">
      <text class="danger-text">{{ error }}</text>
      <button class="btn btn--secondary records-retry" @click="load">重试</button>
    </view>
    <TimelineList v-else :items="timeline?.items || []" editable @edit="edit" />

    <AppNav current="records" />
  </view>
</template>

<style scoped>
.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 18px;
}

.records-add {
  display: flex;
  gap: 4px;
}

.records-add .icon-btn {
  border: 1px solid #dfe5e1;
  background: #ffffff;
}

.date-switch {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  min-height: 52px;
  padding: 4px;
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

.date-switch__value text {
  padding: 2px 6px;
  border-radius: 4px;
  color: #216454;
  background: #e3f0eb;
  font-size: 11px;
}

.record-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 14px;
  padding: 12px 0;
  border-top: 1px solid #dfe5e1;
  border-bottom: 1px solid #dfe5e1;
}

.record-summary view {
  min-width: 0;
  border-right: 1px solid #dfe5e1;
  text-align: center;
}

.record-summary view:last-child { border-right: 0; }
.record-summary text { display: block; color: #74807b; font-size: 10px; }
.record-summary text:first-child { color: #18201d; font-size: 18px; font-weight: 800; }

.timeline-title {
  margin-top: 24px;
}

.records-retry {
  margin: 12px auto 0;
}
</style>
