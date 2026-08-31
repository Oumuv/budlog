<script setup lang="ts">
import { CheckCircle2, ClipboardList, Droplets, Milk, NotebookPen, PackagePlus, Scale } from "lucide-vue-next";
import type { TimelineItem } from "../types";
import { formatTime } from "../utils/date";

defineProps<{ items: TimelineItem[]; editable?: boolean }>();
const emit = defineEmits<{ edit: [item: TimelineItem] }>();
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
      </view>
      <text class="timeline__time">{{ formatTime(item.eventTime) }}</text>
    </view>
  </view>
  <view v-else class="state-panel surface timeline-empty">
    <view class="state-panel__icon"><ClipboardList :size="21" /></view>
    <text class="state-panel__title">还没有记录</text>
    <text class="state-panel__copy">喂奶、存奶、尿便、体重、事件和完成的任务会按时间显示在这里</text>
  </view>
</template>

<style scoped>
.timeline {
  overflow: hidden;
}

.timeline__row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 68px;
  padding: 11px 13px;
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.timeline__row:last-child {
  border-bottom: 0;
}

.timeline__row--action:active {
  background: #fff7f8;
}

.timeline__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 7px;
}

.timeline__icon--feeding {
  color: var(--bud-color-coral);
  background: var(--bud-color-coral-soft);
}

.timeline__icon--diaper {
  color: var(--bud-color-cyan);
  background: var(--bud-color-cyan-soft);
}

.timeline__icon--milk_storage {
  color: var(--bud-color-sage);
  background: var(--bud-color-sage-soft);
}

.timeline__icon--task {
  color: var(--bud-color-gold);
  background: var(--bud-color-gold-soft);
}

.timeline__icon--weight {
  color: #476779;
  background: #eef4f6;
}

.timeline__icon--event {
  color: #72556f;
  background: #f5eff5;
}

.timeline__body {
  min-width: 0;
}

.timeline__title,
.timeline__subtitle {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline__title {
  font-weight: 650;
  line-height: 22px;
}

.timeline__subtitle {
  color: var(--bud-color-muted);
  font-size: 13px;
  line-height: 19px;
}

.timeline__time {
  color: var(--bud-color-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.timeline-empty {
  min-height: 190px;
}
</style>
