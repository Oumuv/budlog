<script setup lang="ts">
import { House, ListOrdered, Settings, SquareCheckBig } from "lucide-vue-next";

const props = defineProps<{ current: "home" | "records" | "tasks" | "settings" }>();

const items = [
  { key: "home", label: "首页", url: "/pages/home/index", icon: House },
  { key: "records", label: "记录", url: "/pages/records/index", icon: ListOrdered },
  { key: "tasks", label: "任务", url: "/pages/tasks/index", icon: SquareCheckBig },
  { key: "settings", label: "设置", url: "/pages/settings/index", icon: Settings },
] as const;

function go(event: { value: string | number }) {
  const item = items.find((candidate) => candidate.key === event.value);
  if (item && item.key !== props.current) uni.redirectTo({ url: item.url });
}
</script>

<template>
  <wd-tabbar
    :model-value="current"
    fixed
    placeholder
    safe-area-inset-bottom
    :z-index="10"
    :bordered="false"
    active-color="#b94b5d"
    inactive-color="#7d6c6f"
    custom-class="bud-tabbar"
    @change="go"
  >
    <wd-tabbar-item
      v-for="item in items"
      :key="item.key"
      :name="item.key"
      :title="item.label"
    >
      <template #icon="{ active }">
        <component :is="item.icon" :size="21" :stroke-width="active ? 2.4 : 2" />
      </template>
    </wd-tabbar-item>
  </wd-tabbar>
</template>

<style scoped>
:deep(.bud-tabbar) {
  --wot-tabbar-height: 62px;
  --wot-tabbar-item-title-font-size: 11px;
  --wot-tabbar-item-title-line-height: 16px;
  padding-right: max(8px, calc((100vw - 740px) / 2));
  padding-left: max(8px, calc((100vw - 740px) / 2));
  border-top: 1px solid var(--bud-color-line);
  background: rgba(255, 253, 253, 0.97);
  box-shadow: 0 -5px 20px rgba(112, 70, 76, 0.08);
  backdrop-filter: blur(14px);
}
</style>
