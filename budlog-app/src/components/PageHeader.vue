<script setup lang="ts">
import { ArrowLeft } from "lucide-vue-next";

defineProps<{ title: string; back?: boolean }>();

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) uni.navigateBack({ success() {} });
  else uni.reLaunch({ url: "/pages/home/index" });
}
</script>

<template>
  <view class="page-header">
    <button v-if="back" class="icon-btn" aria-label="返回" title="返回" @click="goBack">
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
  margin-bottom: 18px;
}

.page-header .icon-btn {
  border: 1px solid var(--bud-color-line);
  background: #ffffff;
  box-shadow: var(--bud-shadow-sm);
}

.page-header .icon-btn:active {
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.page-header__title {
  grid-column: 2;
  overflow: hidden;
  font-size: 18px;
  font-weight: 760;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-header__action {
  display: flex;
  justify-content: flex-end;
}
</style>
