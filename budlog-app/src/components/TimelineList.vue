<script setup lang="ts">
import { CheckCircle2, ChevronRight, ClipboardList, Droplets, Milk, NotebookPen, PackagePlus, Scale } from "lucide-vue-next";
import type { TimelineItem } from "../types";
import { formatDuration, formatTime } from "../utils/date";

defineProps<{ items: TimelineItem[]; editable?: boolean }>();
const emit = defineEmits<{ edit: [item: TimelineItem] }>();

function intervalText(item: TimelineItem): string {
  if (item.minutesSincePrevious === undefined || item.minutesSincePrevious === null) return "";
  const category = item.category === "FEEDING" ? "喂奶" : "尿便";
  return `距上次${category} ${formatDuration(item.minutesSincePrevious)}`;
}
</script>

<template>
  <view v-if="items.length" class="timeline surface">
    <view
      v-for="item in items"
      :key="`${item.category}-${item.id}`"
      class="timeline__row"
      :class="{ 'timeline__row--action': editable && item.category !== 'TASK' }"
      @click="editable && item.category !== 'TASK' && emit('edit', item)"
    >
      <text class="timeline__time">{{ formatTime(item.eventTime) }}</text>
      <view class="timeline__rail"><text class="timeline__dot" /></view>
      <view class="timeline__icon" :class="`timeline__icon--${item.category.toLowerCase()}`">
        <Milk v-if="item.category === 'FEEDING'" :size="18" />
        <Droplets v-else-if="item.category === 'DIAPER'" :size="18" />
        <PackagePlus v-else-if="item.category === 'MILK_STORAGE'" :size="18" />
        <Scale v-else-if="item.category === 'WEIGHT'" :size="18" />
        <NotebookPen v-else-if="item.category === 'EVENT'" :size="18" />
        <CheckCircle2 v-else :size="18" />
      </view>
      <view class="timeline__body">
        <text class="timeline__title">{{ item.title }}</text>
        <text v-if="item.subtitle" class="timeline__subtitle">{{ item.subtitle }}</text>
        <text v-if="item.minutesSincePrevious != null" class="timeline__interval">{{ intervalText(item) }}</text>
      </view>
      <ChevronRight v-if="editable && item.category !== 'TASK'" :size="16" class="timeline__chevron" />
    </view>
  </view>
  <view v-else class="state-panel surface timeline-empty">
    <view class="state-panel__icon"><ClipboardList :size="21" /></view>
    <text class="state-panel__title">还没有记录</text>
    <text class="state-panel__copy">这一天暂无当前分类的记录</text>
  </view>
</template>

<style scoped>
.timeline { overflow: hidden; }
.timeline__row { position: relative; display: grid; min-height: 66px; grid-template-columns: 43px 10px 36px minmax(0, 1fr) 16px; align-items: center; gap: 7px; padding: 9px 8px 9px 4px; border-bottom: 1px solid var(--bud-color-line-soft); }
.timeline__row:last-child { border-bottom: 0; }
.timeline__row--action:active { background: #fff7fa; }
.timeline__time { align-self: start; padding-top: 13px; color: #7584a2; font-size: 11px; font-variant-numeric: tabular-nums; text-align: right; }
.timeline__rail { position: relative; align-self: stretch; }
.timeline__rail::before { position: absolute; top: -10px; bottom: -10px; left: 50%; width: 1px; background: #dfe6f0; content: ""; transform: translateX(-50%); }
.timeline__row:first-child .timeline__rail::before { top: 50%; }
.timeline__row:last-child .timeline__rail::before { bottom: 50%; }
.timeline__dot { position: absolute; z-index: 1; top: 50%; left: 50%; width: 5px; height: 5px; border: 1px solid #fff; border-radius: 50%; background: #a7b4c9; box-shadow: 0 0 0 2px #e6ebf2; transform: translate(-50%, -50%); }
.timeline__icon { display: flex; width: 34px; height: 34px; align-items: center; justify-content: center; border-radius: 8px; }
.timeline__icon--feeding { color: var(--bud-color-coral); background: var(--bud-color-coral-soft); }
.timeline__icon--diaper { color: var(--baby-orange); background: var(--baby-yellow-soft); }
.timeline__icon--milk_storage { color: var(--baby-green); background: var(--baby-green-soft); }
.timeline__icon--task { color: var(--baby-blue); background: var(--baby-blue-soft); }
.timeline__icon--weight { color: var(--baby-purple); background: var(--baby-purple-soft); }
.timeline__icon--event { color: var(--baby-cyan); background: var(--baby-cyan-soft); }
.timeline__body { min-width: 0; }
.timeline__title, .timeline__subtitle, .timeline__interval { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.timeline__title { font-size: 13px; font-weight: 750; line-height: 20px; }
.timeline__subtitle { color: var(--bud-color-muted); font-size: 10px; line-height: 15px; }
.timeline__interval { color: var(--bud-color-muted); font-size: 9px; line-height: 14px; }
.timeline__chevron { color: #9aa6bb; }
.timeline-empty { min-height: 190px; }
</style>
