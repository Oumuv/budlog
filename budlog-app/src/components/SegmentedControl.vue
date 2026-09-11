<script setup lang="ts">
import { NRadioButton, NRadioGroup } from "naive-ui";

type Value = string | number;

defineProps<{
  modelValue: Value;
  options: Array<{ label: string; value: Value }>;
}>();
const emit = defineEmits<{ "update:modelValue": [value: Value] }>();

function updateValue(value: Value | null) {
  if (value !== null) emit("update:modelValue", value);
}
</script>

<template>
  <NRadioGroup
    class="segmented-control"
    :value="modelValue"
    size="large"
    @update:value="updateValue"
  >
    <NRadioButton
      v-for="option in options"
      :key="option.value"
      :value="option.value"
      :label="option.label"
    />
  </NRadioGroup>
</template>

<style scoped>
.segmented-control {
  display: flex;
  width: 100%;
  flex-wrap: nowrap;
}

:deep(.n-radio-button) {
  width: 100%;
  min-width: 0;
  flex: 1 1 0;
}

:deep(.n-radio-button__state-border),
:deep(.n-radio-button__state-border::before) {
  border-radius: 7px;
}

:deep(.n-radio__label) {
  display: block;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
