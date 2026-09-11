<script setup lang="ts">
import { Baby, Heart } from "lucide-vue-next";

withDefaults(defineProps<{ size?: "small" | "large" }>(), {
  size: "small",
});
</script>

<template>
  <view class="brand-mark" :class="`brand-mark--${size}`" aria-hidden="true">
    <Baby :size="size === 'large' ? 30 : 21" :stroke-width="2.2" />
    <view class="brand-mark__heart"><Heart :size="size === 'large' ? 13 : 10" :stroke-width="2.6" /></view>
  </view>
</template>

<style scoped>
.brand-mark {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 93, 143, 0.24);
  border-radius: 8px;
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-primary-soft);
}

.brand-mark__heart {
  position: absolute;
  right: -4px;
  bottom: -4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  color: #ffffff;
  background: var(--bud-color-sage);
}

.brand-mark--small {
  width: 38px;
  height: 38px;
}

.brand-mark--large {
  width: 60px;
  height: 60px;
  color: #ffffff;
  background: var(--bud-color-primary);
  box-shadow: 0 10px 24px rgba(255, 93, 143, 0.24);
}

.brand-mark--large .brand-mark__heart {
  right: -5px;
  bottom: -5px;
  width: 22px;
  height: 22px;
}
</style>
