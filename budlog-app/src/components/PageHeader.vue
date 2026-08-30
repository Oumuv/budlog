<script setup lang="ts">
import { ArrowLeft } from "lucide-vue-next";

defineProps<{ title: string; back?: boolean }>();

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: "/pages/home/home" });
}
</script>

<template>
  <view class="page-header">
    <button v-if="back" class="icon-btn" aria-label="返回" @click="goBack">
      <ArrowLeft :size="22" />
    </button>
    <text class="page-header__title">{{ title }}</text>
    <view class="page-header__action"><slot /></view>
  </view>
</template>

<style scoped>
.page-header {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  align-items: center;
  min-height: 48px;
  margin-bottom: 14px;
}

.page-header__title {
  grid-column: 2;
  overflow: hidden;
  font-size: 18px;
  font-weight: 750;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-header__action {
  display: flex;
  justify-content: flex-end;
}
</style>

