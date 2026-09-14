<script setup lang="ts">
import { AlertCircle } from "lucide-vue-next";
import { NButton } from "naive-ui";

withDefaults(defineProps<{ title?: string; copy: string }>(), {
  title: "暂时无法加载",
});
defineEmits<{ retry: [] }>();
</script>

<template>
  <view class="state-panel surface">
    <view class="state-panel__icon error-state__icon"><AlertCircle :size="22" /></view>
    <text class="state-panel__title">{{ title }}</text>
    <text class="state-panel__copy">{{ copy }}</text>
    <NButton secondary type="primary" @click="$emit('retry')">重新加载</NButton>
  </view>
</template>

<style scoped>
.error-state__icon {
  color: var(--baby-danger);
  background: var(--baby-danger-soft);
}
</style>
