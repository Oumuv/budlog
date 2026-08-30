<script setup lang="ts">
import { computed } from "vue";
import { nowLocalInput, toIso, toLocalInput } from "../utils/date";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const pickerValue = computed({
  get() {
    try {
      return new Date(toIso(props.modelValue || nowLocalInput())).getTime();
    } catch {
      return Date.now();
    }
  },
  set(value: string | number) {
    emit("update:modelValue", toLocalInput(new Date(Number(value)).toISOString()));
  },
});

const minDate = new Date(2000, 0, 1).getTime();
const maxDate = new Date(2100, 11, 31, 23, 59).getTime();
</script>

<template>
  <wd-datetime-picker
    v-model="pickerValue"
    type="datetime"
    title="选择日期和时间"
    :min-date="minDate"
    :max-date="maxDate"
    :z-index="60"
    root-portal
    custom-class="date-time-field"
    custom-cell-class="date-time-field__cell"
  />
</template>

<style scoped>
:deep(.date-time-field__cell) {
  min-height: 48px;
  padding: 0 13px;
  border: 1px solid #cdd8d2;
  border-radius: 7px;
  background: #ffffff;
  font-variant-numeric: tabular-nums;
}
</style>
