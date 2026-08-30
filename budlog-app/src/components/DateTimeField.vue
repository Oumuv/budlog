<script setup lang="ts">
import { CalendarClock, ChevronRight } from "lucide-vue-next";
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
    <wd-datetime-picker
      v-model="pickerValue"
      type="datetime"
      :title="title"
      :min-date="resolvedMinDate"
      :max-date="resolvedMaxDate"
      :z-index="60"
      root-portal
      custom-class="date-time-field__picker"
      @open="refreshCurrentBoundary"
    >
      <view class="date-time-field__trigger">
        <view class="date-time-field__icon"><CalendarClock :size="19" /></view>
        <text class="date-time-field__value">{{ displayValue }}</text>
        <ChevronRight class="date-time-field__arrow" :size="18" />
      </view>
    </wd-datetime-picker>
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
.date-time-field__trigger {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 18px;
  align-items: center;
  min-height: 50px;
  padding: 0 13px 0 10px;
  border: 1px solid var(--wot-color-border);
  border-radius: 10px;
  background: #ffffff;
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}

.date-time-field__trigger:active {
  border-color: rgba(185, 75, 93, 0.5);
  background: #fffafa;
  box-shadow: 0 0 0 3px rgba(185, 75, 93, 0.08);
}

.date-time-field__icon {
  display: flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.date-time-field__value {
  min-width: 0;
  overflow: hidden;
  color: var(--bud-color-ink);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.date-time-field__arrow {
  color: var(--bud-color-muted);
}

.date-time-field__shortcuts {
  margin-top: 9px;
}
</style>
