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
  right: auto;
  bottom: 0;
  left: 50%;
  width: min(100%, 430px);
  padding: 5px 9px calc(5px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--bud-color-line);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 -5px 18px rgba(49, 70, 109, 0.06);
  backdrop-filter: blur(14px);
  transform: translateX(-50%);
}

.app-nav__inner {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  max-width: 412px;
  margin: 0 auto;
}

.app-nav__item {
  display: flex;
  min-width: 0;
  min-height: 47px;
  margin: 0;
  padding: 4px 2px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  border: 0;
  border-radius: 9px;
  color: var(--bud-color-muted);
  background: transparent;
  font-size: 10px;
  line-height: 15px;
}

.app-nav__item--active {
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
  font-weight: 700;
}
</style>
