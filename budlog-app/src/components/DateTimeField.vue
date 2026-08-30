<script setup lang="ts">
import { computed } from "vue";
import { nowLocalInput } from "../utils/date";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const normalized = computed(() => props.modelValue || nowLocalInput());
const datePart = computed(() => normalized.value.slice(0, 10));
const timePart = computed(() => normalized.value.slice(11, 16));

function updateDate(event: { detail: { value: string } }) {
  emit("update:modelValue", `${event.detail.value}T${timePart.value}`);
}

function updateTime(event: { detail: { value: string } }) {
  emit("update:modelValue", `${datePart.value}T${event.detail.value}`);
}
</script>

<template>
  <view class="date-time-field">
    <picker mode="date" :value="datePart" @change="updateDate">
      <view class="field__control date-time-field__control">{{ datePart }}</view>
    </picker>
    <picker mode="time" :value="timePart" @change="updateTime">
      <view class="field__control date-time-field__control">{{ timePart }}</view>
    </picker>
  </view>
</template>

<style scoped>
.date-time-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  gap: 8px;
}

.date-time-field__control {
  display: flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
}
</style>

