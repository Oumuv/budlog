<template>
  <view class="stat-card">
    <view class="label">{{ label }}</view>
    <view class="value">{{ value }}</view>
    <view v-if="desc" class="desc">{{ desc }}</view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  desc?: string
}>()
</script>

<style scoped lang="scss">
.stat-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
}
.label {
  color: #888;
  font-size: 24rpx;
}
.value {
  margin-top: 12rpx;
  font-size: 44rpx;
  font-weight: 700;
}
.desc {
  margin-top: 8rpx;
  color: #aaa;
  font-size: 22rpx;
}
</style>
