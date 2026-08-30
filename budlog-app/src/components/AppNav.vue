<script setup lang="ts">
import { House, ListOrdered, Settings, SquareCheckBig } from "lucide-vue-next";

const props = defineProps<{ current: "home" | "records" | "tasks" | "settings" }>();

const items = [
  { key: "home", label: "首页", url: "/pages/home/home", icon: House },
  { key: "records", label: "记录", url: "/pages/records/records", icon: ListOrdered },
  { key: "tasks", label: "任务", url: "/pages/tasks/tasks", icon: SquareCheckBig },
  { key: "settings", label: "设置", url: "/pages/settings/settings", icon: Settings },
] as const;

function go(key: string, url: string) {
  if (key !== props.current) uni.redirectTo({ url });
}
</script>

<template>
  <view class="app-nav">
    <button
      v-for="item in items"
      :key="item.key"
      class="app-nav__item"
      :class="{ 'app-nav__item--active': item.key === current }"
      :aria-label="item.label"
      @click="go(item.key, item.url)"
    >
      <component :is="item.icon" :size="21" :stroke-width="2" />
      <text>{{ item.label }}</text>
    </button>
  </view>
</template>

<style scoped>
.app-nav {
  position: fixed;
  z-index: 20;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  height: calc(64px + env(safe-area-inset-bottom));
  padding: 5px max(8px, calc((100vw - 720px) / 2)) env(safe-area-inset-bottom);
  border-top: 1px solid #dfe5e1;
  background: rgba(255, 255, 255, 0.97);
}

.app-nav__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  height: 54px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  color: #78837f;
  background: transparent;
  font-size: 11px;
  line-height: 15px;
}

.app-nav__item--active {
  color: #216454;
  font-weight: 700;
}
</style>

