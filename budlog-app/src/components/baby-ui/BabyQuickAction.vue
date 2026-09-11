<script setup lang="ts">
withDefaults(defineProps<{
  icon?: string;
  title: string;
  subtitle?: string;
}>(), {
  subtitle: ''
});

defineEmits<{
  click: [];
}>();
</script>

<template>
  <button class="baby-quick-action" @click="$emit('click')">
    <view class="baby-quick-action__icon">
      <slot name="icon">{{ icon }}</slot>
    </view>
    <view class="baby-quick-action__body">
      <text class="baby-quick-action__title">{{ title }}</text>
      <text v-if="subtitle" class="baby-quick-action__subtitle">{{ subtitle }}</text>
    </view>
  </button>
</template>

<style scoped>
.baby-quick-action {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--bud-color-line);
  text-align: left;
}
.baby-quick-action__icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(255,159,178,.16);
}
.baby-quick-action__title {
  display:block;
  font-size:15px;
  font-weight:700;
}
.baby-quick-action__subtitle {
  display:block;
  margin-top:2px;
  color:var(--bud-color-muted);
  font-size:12px;
}
</style>
