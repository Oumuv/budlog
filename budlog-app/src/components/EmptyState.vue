<script setup lang="ts">
import { Inbox } from "lucide-vue-next";

defineProps<{ title: string; copy?: string }>();
</script>

<template>
  <view class="state-panel surface">
    <view class="state-panel__icon"><Inbox :size="21" /></view>
    <text class="state-panel__title">{{ title }}</text>
    <text v-if="copy" class="state-panel__copy">{{ copy }}</text>
    <slot />
  </view>
</template>
