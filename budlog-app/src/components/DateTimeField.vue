<script setup lang="ts">
import { NDatePicker } from "naive-ui";
import { computed, ref } from "vue";
import { nowLocalInput, toIso, toLocalInput, todayKey } from "../utils/date";

const props = withDefaults(defineProps<{
  modelValue: string;
  title?: string;
  quickRecord?: boolean;
  minDate?: number;
  maxDate?: number;
  maxNowOffsetMinutes?: number;
}>(), {
  title: "选择日期和时间",
  quickRecord: false,
});
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const defaultMinDate = new Date(2000, 0, 1).getTime();
const defaultMaxDate = new Date(2100, 11, 31, 23, 59).getTime();
const currentBoundary = ref(Date.now());
const resolvedMinDate = computed(() => props.minDate ?? defaultMinDate);
const resolvedMaxDate = computed(() => {
  if (props.maxNowOffsetMinutes !== undefined) return currentBoundary.value + props.maxNowOffsetMinutes * 60_000;
  return props.maxDate ?? defaultMaxDate;
});
const recordShortcuts = [
  { label: "现在", offsetMinutes: 0 },
  { label: "5 分钟前", offsetMinutes: -5 },
  { label: "15 分钟前", offsetMinutes: -15 },
];

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

const displayValue = computed(() => {
  const [date = "", time = ""] = props.modelValue.split("T");
  if (!date || !time) return "请选择时间";
  if (date === todayKey()) return `今天 ${time}`;
  const [year, month, day] = date.split("-");
  return `${year}年${month}月${day}日 ${time}`;
});

function selectShortcut(offsetMinutes: number) {
  refreshCurrentBoundary();
  const instant = Date.now() + offsetMinutes * 60_000;
  const clamped = Math.min(resolvedMaxDate.value, Math.max(resolvedMinDate.value, instant));
  emit("update:modelValue", toLocalInput(new Date(clamped).toISOString()));
}

function refreshCurrentBoundary() {
  currentBoundary.value = Date.now();
}

function shortcutActive(offsetMinutes: number) {
  try {
    return Math.abs(new Date(toIso(props.modelValue)).getTime() - (Date.now() + offsetMinutes * 60_000)) < 60_000;
  } catch {
    return false;
  }
}
</script>

<template>
  <view class="date-time-field">
    <NDatePicker
      v-model:value="pickerValue"
      class="date-time-field__picker"
      type="datetime"
      :placeholder="title"
      :min-date="resolvedMinDate"
      :max-date="resolvedMaxDate"
      :clearable="false"
      format="yyyy-MM-dd HH:mm"
      @focus="refreshCurrentBoundary"
    />
    <text class="date-time-field__summary">{{ displayValue }}</text>
    <view v-if="quickRecord" class="shortcut-row date-time-field__shortcuts">
      <button
        v-for="shortcut in recordShortcuts"
        :key="shortcut.label"
        class="shortcut-chip"
        :class="{ 'shortcut-chip--active': shortcutActive(shortcut.offsetMinutes) }"
        @click="selectShortcut(shortcut.offsetMinutes)"
      >
        {{ shortcut.label }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.date-time-field__picker {
  width: 100%;
}

.date-time-field__summary {
  display: block;
  margin-top: 6px;
  color: var(--bud-color-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.date-time-field__shortcuts {
  margin-top: 9px;
}
</style>
