<script setup lang="ts">
import { NSpin } from "naive-ui";

withDefaults(defineProps<{ copy?: string }>(), {
  copy: "正在加载",
});
</script>

<template>
  <view class="state-panel surface">
    <NSpin size="small" />
    <text class="state-panel__copy">{{ copy }}</text>
  </view>
</template>
