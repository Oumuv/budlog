<script setup lang="ts">
import { ChartLine, House, ListOrdered, Settings, SquareCheckBig } from "lucide-vue-next";

const props = defineProps<{ current: "home" | "records" | "analytics" | "tasks" | "settings" }>();

const items = [
  { key: "home", label: "首页", url: "/pages/home/index", icon: House },
  { key: "records", label: "记录", url: "/pages/records/index", icon: ListOrdered },
  { key: "analytics", label: "趋势", url: "/pages/analytics/index", icon: ChartLine },
  { key: "tasks", label: "任务", url: "/pages/tasks/index", icon: SquareCheckBig },
  { key: "settings", label: "设置", url: "/pages/settings/index", icon: Settings },
] as const;

function go(key: string) {
  const item = items.find((candidate) => candidate.key === key);
  if (item && item.key !== props.current) uni.redirectTo({ url: item.url });
}
</script>

<template>
  <view class="app-nav" aria-label="主导航">
    <view class="app-nav__inner">
      <button
      v-for="item in items"
      :key="item.key"
        class="app-nav__item"
        :class="{ 'app-nav__item--active': current === item.key }"
        :aria-current="current === item.key ? 'page' : undefined"
        @click="go(item.key)"
    >
        <component :is="item.icon" :size="21" :stroke-width="current === item.key ? 2.5 : 2" />
        <text>{{ item.label }}</text>
      </button>
    </view>
  </view>
</template>

<style scoped>
.app-nav {
  position: fixed;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 7px max(8px, calc((100vw - 740px) / 2)) calc(7px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--bud-color-line);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -6px 22px rgba(45, 55, 72, 0.08);
  backdrop-filter: blur(14px);
}

.app-nav__inner {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  max-width: 720px;
  margin: 0 auto;
}

.app-nav__item {
  display: flex;
  min-width: 0;
  min-height: 48px;
  margin: 0;
  padding: 4px 2px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 0;
  border-radius: 8px;
  color: var(--bud-color-muted);
  background: transparent;
  font-size: 11px;
  line-height: 16px;
}

.app-nav__item--active {
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
  font-weight: 700;
}
</style>
