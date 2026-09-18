<script setup lang="ts">
import { CalendarClock, ChevronRight } from "lucide-vue-next";
import { NDatePicker } from "naive-ui";
import { computed, ref } from "vue";
import { nowLocalInput, toIso, toLocalInput, todayKey } from "../utils/date";

type DateTimeParts = [number, number, number, number, number];

interface MultiSelectorChangeEvent {
  detail: { value: Array<number | string> };
}

interface MultiSelectorColumnChangeEvent {
  detail: { column: number | string; value: number | string };
}

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
const pickerColumns = ref<string[][]>([]);
const pickerIndexes = ref<number[]>([0, 0, 0, 0, 0]);
const resolvedMinDate = computed(() => props.minDate ?? defaultMinDate);
const resolvedMaxDate = computed(() => {
  if (props.maxNowOffsetMinutes !== undefined) return currentBoundary.value + props.maxNowOffsetMinutes * 60_000;
  return props.maxDate ?? defaultMaxDate;
});
const pickerMinDate = computed(() => Math.ceil(resolvedMinDate.value / 60_000) * 60_000);
const pickerMaxDate = computed(() => Math.floor(resolvedMaxDate.value / 60_000) * 60_000);
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

function preparePicker() {
  refreshCurrentBoundary();
  const min = pickerMinDate.value;
  const max = pickerMaxDate.value;
  if (max < min) {
    showPickerError("日期时间范围配置无效");
    return;
  }

  let instant = Date.now();
  try {
    instant = new Date(toIso(props.modelValue || nowLocalInput())).getTime();
  } catch {
    // Invalid external values fall back to the current time before clamping.
  }
  const clamped = Math.min(max, Math.max(min, instant));
  rebuildPicker(parseLocalParts(toLocalInput(new Date(clamped).toISOString())));
}

function updatePickerColumn(event: MultiSelectorColumnChangeEvent) {
  const column = Number(event.detail.column);
  const value = Number(event.detail.value);
  if (!Number.isInteger(column) || column < 0 || column > 4 || !Number.isInteger(value)) return;

  const indexes = [...pickerIndexes.value];
  indexes[column] = value;
  rebuildPicker(partsFromIndexes(indexes));
}

function confirmPicker(event: MultiSelectorChangeEvent) {
  refreshCurrentBoundary();
  const value = formatLocalParts(partsFromIndexes(event.detail.value.map(Number)));
  try {
    const instant = new Date(toIso(value)).getTime();
    if (instant < resolvedMinDate.value) {
      showPickerError(`不能早于${formatBoundary(resolvedMinDate.value)}`);
      return;
    }
    if (instant > resolvedMaxDate.value) {
      showPickerError(props.maxNowOffsetMinutes !== undefined
        ? "所选时间不能晚于当前允许时间"
        : `不能晚于${formatBoundary(resolvedMaxDate.value)}`);
      return;
    }
    emit("update:modelValue", value);
  } catch (exception) {
    showPickerError(exception instanceof Error ? exception.message : "请选择有效的日期和时间");
  }
}

function rebuildPicker(preferred: DateTimeParts) {
  const min = parseLocalParts(toLocalInput(new Date(pickerMinDate.value).toISOString()));
  const max = parseLocalParts(toLocalInput(new Date(pickerMaxDate.value).toISOString()));

  const year = clamp(preferred[0], min[0], max[0]);
  const monthStart = year === min[0] ? min[1] : 1;
  const monthEnd = year === max[0] ? max[1] : 12;
  const month = clamp(preferred[1], monthStart, monthEnd);

  const atMinMonth = year === min[0] && month === min[1];
  const atMaxMonth = year === max[0] && month === max[1];
  const dayStart = atMinMonth ? min[2] : 1;
  const dayEnd = atMaxMonth ? max[2] : daysInMonth(year, month);
  const day = clamp(preferred[2], dayStart, dayEnd);

  const atMinDay = atMinMonth && day === min[2];
  const atMaxDay = atMaxMonth && day === max[2];
  const hourStart = atMinDay ? min[3] : 0;
  const hourEnd = atMaxDay ? max[3] : 23;
  const hour = clamp(preferred[3], hourStart, hourEnd);

  const atMinHour = atMinDay && hour === min[3];
  const atMaxHour = atMaxDay && hour === max[3];
  const minuteStart = atMinHour ? min[4] : 0;
  const minuteEnd = atMaxHour ? max[4] : 59;
  const minute = clamp(preferred[4], minuteStart, minuteEnd);

  pickerColumns.value = [
    createOptions(min[0], max[0], "年", false),
    createOptions(monthStart, monthEnd, "月"),
    createOptions(dayStart, dayEnd, "日"),
    createOptions(hourStart, hourEnd, "时"),
    createOptions(minuteStart, minuteEnd, "分"),
  ];
  pickerIndexes.value = [
    year - min[0],
    month - monthStart,
    day - dayStart,
    hour - hourStart,
    minute - minuteStart,
  ];
}

function partsFromIndexes(indexes: number[]): DateTimeParts {
  return pickerColumns.value.map((options, column) => {
    const fallback = pickerIndexes.value[column] ?? 0;
    const index = clamp(Number.isFinite(indexes[column]) ? indexes[column] : fallback, 0, Math.max(0, options.length - 1));
    return Number.parseInt(options[index] || "0", 10);
  }) as DateTimeParts;
}

function parseLocalParts(value: string): DateTimeParts {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) throw new Error("时间格式不正确");
  return match.slice(1).map(Number) as DateTimeParts;
}

function formatLocalParts(parts: DateTimeParts): string {
  return `${parts[0]}-${pad(parts[1])}-${pad(parts[2])}T${pad(parts[3])}:${pad(parts[4])}`;
}

function formatBoundary(instant: number): string {
  return toLocalInput(new Date(instant).toISOString()).replace("T", " ");
}

function createOptions(start: number, end: number, suffix: string, padded = true): string[] {
  return Array.from({ length: end - start + 1 }, (_, index) => {
    const value = start + index;
    return `${padded ? pad(value) : value}${suffix}`;
  });
}

function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function showPickerError(title: string) {
  uni.showToast({ title, icon: "none" });
}

preparePicker();
</script>

<template>
  <view class="date-time-field">
    <NDatePicker
      v-model:value="pickerValue"
      class="date-time-field__desktop"
      type="datetime"
      :placeholder="title"
      :min-date="resolvedMinDate"
      :max-date="resolvedMaxDate"
      :clearable="false"
      format="yyyy-MM-dd HH:mm"
      @focus="refreshCurrentBoundary"
    />
    <picker
      class="date-time-field__mobile-picker"
      mode="multiSelector"
      :range="pickerColumns"
      :value="pickerIndexes"
      @click="preparePicker"
      @columnchange="updatePickerColumn"
      @change="confirmPicker"
    >
      <button
        class="date-time-field__mobile-trigger"
        :aria-label="`${title}，当前为${displayValue}`"
        hover-class="none"
      >
        <view class="date-time-field__mobile-icon"><CalendarClock :size="20" /></view>
        <text class="date-time-field__mobile-value">{{ displayValue }}</text>
        <ChevronRight class="date-time-field__mobile-arrow" :size="19" />
      </button>
    </picker>
    <view v-if="quickRecord" class="date-time-field__shortcuts">
      <button
        v-for="shortcut in recordShortcuts"
        :key="shortcut.label"
        class="date-time-field__shortcut"
        :class="{ 'date-time-field__shortcut--active': shortcutActive(shortcut.offsetMinutes) }"
        :aria-pressed="shortcutActive(shortcut.offsetMinutes)"
        hover-class="none"
        @click="selectShortcut(shortcut.offsetMinutes)"
      >
        {{ shortcut.label }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.date-time-field__desktop {
  width: 100%;
}

.date-time-field__mobile-picker,
.date-time-field__mobile-trigger {
  display: none;
}

.date-time-field__shortcuts {
  display: none;
  gap: 7px;
  margin-top: 9px;
}

.date-time-field__shortcut {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  min-height: 36px;
  appearance: none;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 14px;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  color: var(--bud-color-ink);
  background: var(--bud-color-surface);
  cursor: pointer;
  font-size: 14px;
  font-weight: 650;
  line-height: 20px;
  touch-action: manipulation;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease, transform 0.1s ease;
}

.date-time-field__shortcut--active {
  border-color: var(--bud-color-primary-dark);
  color: #ffffff;
  background: var(--bud-color-primary-dark);
  box-shadow: 0 2px 7px rgba(201, 54, 105, 0.2);
}

.date-time-field__shortcut:active:not(.date-time-field__shortcut--active) {
  border-color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
  transform: scale(0.98);
}

.date-time-field__shortcut--active:active {
  border-color: var(--baby-primary-pressed);
  background: var(--baby-primary-pressed);
  transform: scale(0.98);
}

.date-time-field__shortcut:focus-visible {
  outline: 2px solid rgba(255, 93, 143, 0.32);
  outline-offset: 2px;
}

@media (max-width: 768px), (pointer: coarse) {
  .date-time-field__desktop {
    display: none;
  }

  .date-time-field__mobile-picker {
    display: block;
  }

  .date-time-field__mobile-trigger {
    box-sizing: border-box;
    display: grid;
    width: 100%;
    min-height: 52px;
    appearance: none;
    margin: 0;
    padding: 0 12px 0 9px;
    grid-template-columns: 34px minmax(0, 1fr) 20px;
    align-items: center;
    gap: 8px;
    border: 1px solid var(--bud-color-line);
    border-radius: 8px;
    color: var(--bud-color-ink);
    background: var(--bud-color-surface);
    cursor: pointer;
    text-align: left;
    touch-action: manipulation;
    transition: border-color 0.16s ease, background-color 0.16s ease;
  }

  .date-time-field__mobile-trigger:active {
    border-color: var(--bud-color-primary);
    background: var(--bud-color-primary-soft);
  }

  .date-time-field__mobile-trigger:focus-visible {
    outline: 2px solid rgba(255, 93, 143, 0.32);
    outline-offset: 2px;
  }

  .date-time-field__mobile-icon {
    display: flex;
    width: 32px;
    height: 32px;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    color: var(--bud-color-primary);
    background: var(--bud-color-primary-soft);
  }

  .date-time-field__mobile-value {
    min-width: 0;
    overflow: hidden;
    font-size: 15px;
    font-variant-numeric: tabular-nums;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .date-time-field__mobile-arrow {
    color: var(--bud-color-muted);
  }

  .date-time-field__shortcuts {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .date-time-field__shortcut {
    min-width: 0;
    min-height: 44px;
    padding: 0 4px;
  }
}
</style>
