<script setup lang="ts">
import { CheckCircle2, Droplets, Milk } from "lucide-vue-next";
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
        <CheckCircle2 v-else :size="18" />
      </view>
      <view class="timeline__body">
        <text class="timeline__title">{{ item.title }}</text>
        <text v-if="item.subtitle" class="timeline__subtitle">{{ item.subtitle }}</text>
      </view>
      <text class="timeline__time">{{ formatTime(item.eventTime) }}</text>
    </view>
  </view>
  <view v-else class="empty-state surface">当天暂无记录</view>
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
  min-height: 64px;
  padding: 10px 12px;
  border-bottom: 1px solid #edf0ee;
}

.timeline__row:last-child {
  border-bottom: 0;
}

.timeline__row--action:active {
  background: #f5f8f6;
}

.timeline__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
}

.timeline__icon--feeding {
  color: #a64b3f;
  background: #fbe9e4;
}

.timeline__icon--diaper {
  color: #176e78;
  background: #e2f1f2;
}

.timeline__icon--task {
  color: #6a5713;
  background: #f7efc9;
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
  color: #74807b;
  font-size: 13px;
  line-height: 19px;
}

.timeline__time {
  color: #65726d;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
</style>
