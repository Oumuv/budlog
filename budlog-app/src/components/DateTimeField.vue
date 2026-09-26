<script setup lang="ts">
import { AlertCircle, CalendarClock, Check, ChevronRight, X } from "lucide-vue-next";
import { NButton, NDatePicker, NDrawer } from "naive-ui";
import { computed, ref } from "vue";
import { nowLocalInput, shiftDay, toIso, toLocalInput, todayKey } from "../utils/date";

type DayMode = "TODAY" | "YESTERDAY" | "OTHER";

const props = withDefaults(defineProps<{
  modelValue: string;
  title?: string;
  quickRecord?: boolean;
  dateOnly?: boolean;
  minDate?: number;
  maxDate?: number;
  maxNowOffsetMinutes?: number;
}>(), {
  title: "选择日期和时间",
  quickRecord: false,
  dateOnly: false,
});
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const defaultMinDate = new Date(2000, 0, 1).getTime();
const defaultMaxDate = new Date(2100, 11, 31, 23, 59).getTime();
const currentBoundary = ref(Date.now());
const sheetOpen = ref(false);
const draftDayMode = ref<DayMode>("TODAY");
const draftDate = ref("");
const draftTime = ref("");
const draftError = ref("");
const resolvedMinDate = computed(() => props.minDate ?? defaultMinDate);
const resolvedMaxDate = computed(() => {
  if (props.maxNowOffsetMinutes !== undefined) return currentBoundary.value + props.maxNowOffsetMinutes * 60_000;
  return props.maxDate ?? defaultMaxDate;
});
const resolvedMinLocal = computed(() => toLocalInput(new Date(resolvedMinDate.value).toISOString()));
const resolvedMaxLocal = computed(() => toLocalInput(new Date(resolvedMaxDate.value).toISOString()));
const minDateKey = computed(() => resolvedMinLocal.value.slice(0, 10));
const maxDateKey = computed(() => resolvedMaxLocal.value.slice(0, 10));
const minTime = computed(() => draftDate.value === minDateKey.value ? resolvedMinLocal.value.slice(11, 16) : undefined);
const maxTime = computed(() => draftDate.value === maxDateKey.value ? resolvedMaxLocal.value.slice(11, 16) : undefined);
const showDayPresets = computed(() => props.quickRecord || props.dateOnly);
const showDatePicker = computed(() => !showDayPresets.value || draftDayMode.value === "OTHER");
const sheetHeight = computed(() => {
  if (props.quickRecord && !props.dateOnly) return "min(520px, calc(100dvh - 12px))";
  if (props.dateOnly) return "min(350px, calc(100dvh - 12px))";
  return "min(420px, calc(100dvh - 12px))";
});
const recordShortcuts = [
  { label: "刚刚", offsetMinutes: 0 },
  { label: "10 分钟前", offsetMinutes: -10 },
  { label: "30 分钟前", offsetMinutes: -30 },
  { label: "1 小时前", offsetMinutes: -60 },
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
    const nextValue = toLocalInput(new Date(Number(value)).toISOString());
    if (!props.dateOnly) {
      emit("update:modelValue", nextValue);
      return;
    }
    const currentValue = validLocalValue(props.modelValue) || nowLocalInput();
    emit("update:modelValue", dateOnlyValue(nextValue.slice(0, 10), currentValue.slice(11, 16)));
  },
});

const displayValue = computed(() => {
  const [date = "", time = ""] = props.modelValue.split("T");
  if (!date || (!props.dateOnly && !time)) return props.dateOnly ? "请选择日期" : "请选择时间";
  const today = todayKey();
  const dayLabel = date === today
    ? "今天"
    : date === shiftDay(today, -1)
      ? "昨天"
      : formatDate(date);
  if (props.dateOnly) return dayLabel;
  const shortcut = recordShortcuts.find(({ offsetMinutes }) => shortcutActive(offsetMinutes));
  if (date === today && shortcut) return `${shortcut.label} · ${time}`;
  return `${dayLabel} ${time}`;
});

function refreshCurrentBoundary() {
  currentBoundary.value = Date.now();
}

function shortcutActive(offsetMinutes: number) {
  try {
    const expected = currentBoundary.value + offsetMinutes * 60_000;
    return Math.abs(new Date(toIso(props.modelValue)).getTime() - expected) < 90_000;
  } catch {
    return false;
  }
}

function selectShortcut(offsetMinutes: number) {
  refreshCurrentBoundary();
  const instant = currentBoundary.value + offsetMinutes * 60_000;
  const clamped = Math.min(resolvedMaxDate.value, Math.max(resolvedMinDate.value, instant));
  emit("update:modelValue", toLocalInput(new Date(clamped).toISOString()));
  sheetOpen.value = false;
}

function openSheet() {
  refreshCurrentBoundary();
  const value = validLocalValue(props.modelValue) || nowLocalInput();
  draftDate.value = value.slice(0, 10);
  draftTime.value = value.slice(11, 16);
  draftDayMode.value = dayModeFor(draftDate.value);
  draftError.value = "";
  sheetOpen.value = true;
}

function closeSheet() {
  sheetOpen.value = false;
  draftError.value = "";
}

function selectDraftDayMode(mode: DayMode) {
  draftDayMode.value = mode;
  if (mode === "TODAY") draftDate.value = todayKey();
  if (mode === "YESTERDAY") draftDate.value = shiftDay(todayKey(), -1);
  draftTime.value = clampedTime(draftDate.value, draftTime.value);
  draftError.value = "";
}

function updateDraftDate(event: { detail: { value: string } }) {
  draftDate.value = event.detail.value;
  draftDayMode.value = dayModeFor(draftDate.value);
  draftError.value = "";
}

function updateDraftTime(event: { detail: { value: string } }) {
  draftTime.value = event.detail.value;
  draftError.value = "";
}

function confirmDraft() {
  refreshCurrentBoundary();
  const value = props.dateOnly ? dateOnlyDraftValue() : `${draftDate.value}T${draftTime.value}`;
  try {
    const instant = new Date(toIso(value)).getTime();
    if (instant < resolvedMinDate.value) {
      draftError.value = `不能早于${formatBoundary(resolvedMinLocal.value)}`;
      return;
    }
    if (instant > resolvedMaxDate.value) {
      draftError.value = props.maxNowOffsetMinutes !== undefined
        ? `所选${props.dateOnly ? "日期" : "时间"}不能晚于当前允许范围`
        : `不能晚于${formatBoundary(resolvedMaxLocal.value)}`;
      return;
    }
    emit("update:modelValue", value);
    closeSheet();
  } catch (exception) {
    draftError.value = exception instanceof Error ? exception.message : "请选择有效的日期和时间";
  }
}

function dateOnlyDraftValue(): string {
  const currentValue = validLocalValue(props.modelValue) || nowLocalInput();
  return dateOnlyValue(draftDate.value, currentValue.slice(11, 16));
}

function dateOnlyValue(date: string, preferredTime: string): string {
  return `${date}T${clampedTime(date, preferredTime)}`;
}

function clampedTime(date: string, preferredTime: string): string {
  let time = preferredTime;
  const minimum = date === minDateKey.value ? resolvedMinLocal.value.slice(11, 16) : undefined;
  const maximum = date === maxDateKey.value ? resolvedMaxLocal.value.slice(11, 16) : undefined;
  if (minimum && time < minimum) time = minimum;
  if (maximum && time > maximum) time = maximum;
  return time;
}

function dayModeFor(date: string): DayMode {
  if (date === todayKey()) return "TODAY";
  if (date === shiftDay(todayKey(), -1)) return "YESTERDAY";
  return "OTHER";
}

function validLocalValue(value: string): string | undefined {
  try {
    toIso(value);
    return value;
  } catch {
    return undefined;
  }
}

function formatDate(value: string): string {
  const [year, month, day] = value.split("-");
  return `${year}年${month}月${day}日`;
}

function formatBoundary(value: string): string {
  const [date, time] = value.split("T");
  return `${date} ${time}`;
}
</script>

<template>
  <view class="date-time-field">
    <NDatePicker
      v-model:value="pickerValue"
      class="date-time-field__desktop"
      :type="dateOnly ? 'date' : 'datetime'"
      :placeholder="title"
      :min-date="resolvedMinDate"
      :max-date="resolvedMaxDate"
      :clearable="false"
      :format="dateOnly ? 'yyyy-MM-dd' : 'yyyy-MM-dd HH:mm'"
      @focus="refreshCurrentBoundary"
    />
    <button
      class="date-time-field__mobile-trigger"
      :aria-label="`${title}，当前为${displayValue}`"
      hover-class="none"
      @click="openSheet"
    >
      <view class="date-time-field__mobile-icon"><CalendarClock :size="20" /></view>
      <text class="date-time-field__mobile-value">{{ displayValue }}</text>
      <text class="date-time-field__mobile-action">修改</text>
    </button>

    <NDrawer
      v-model:show="sheetOpen"
      class="date-time-drawer"
      placement="bottom"
      :height="sheetHeight"
      :z-index="800"
      :auto-focus="false"
    >
      <view class="date-time-sheet">
        <view class="date-time-sheet__handle" />
        <view class="date-time-sheet__header">
          <text class="date-time-sheet__title">{{ title }}</text>
          <text class="date-time-sheet__current">当前选择：{{ displayValue }}</text>
        </view>

        <view v-if="quickRecord && !dateOnly" class="date-time-sheet__shortcuts" aria-label="快捷时间">
          <button
            v-for="shortcut in recordShortcuts"
            :key="shortcut.label"
            class="date-time-sheet__shortcut"
            :class="{ 'date-time-sheet__shortcut--active': shortcutActive(shortcut.offsetMinutes) }"
            :aria-pressed="shortcutActive(shortcut.offsetMinutes)"
            hover-class="none"
            @click="selectShortcut(shortcut.offsetMinutes)"
          >
            {{ shortcut.label }}
          </button>
        </view>

        <view v-if="showDayPresets" class="date-time-sheet__day-presets" aria-label="选择日期范围">
          <button
            class="date-time-sheet__day-button"
            :class="{ 'date-time-sheet__day-button--active': draftDayMode === 'TODAY' }"
            :aria-pressed="draftDayMode === 'TODAY'"
            hover-class="none"
            @click="selectDraftDayMode('TODAY')"
          >今天</button>
          <button
            class="date-time-sheet__day-button"
            :class="{ 'date-time-sheet__day-button--active': draftDayMode === 'YESTERDAY' }"
            :aria-pressed="draftDayMode === 'YESTERDAY'"
            hover-class="none"
            @click="selectDraftDayMode('YESTERDAY')"
          >昨天</button>
          <button
            class="date-time-sheet__day-button"
            :class="{ 'date-time-sheet__day-button--active': draftDayMode === 'OTHER' }"
            :aria-pressed="draftDayMode === 'OTHER'"
            hover-class="none"
            @click="selectDraftDayMode('OTHER')"
          >其他日期</button>
        </view>

        <view class="date-time-sheet__fields">
          <view v-if="showDatePicker" class="date-time-sheet__field">
            <text class="date-time-sheet__label">日期</text>
            <picker
              class="date-time-sheet__picker"
              mode="date"
              :value="draftDate"
              :start="minDateKey"
              :end="maxDateKey"
              @change="updateDraftDate"
            >
              <view class="date-time-sheet__picker-value" aria-label="选择日期">
                <text>{{ draftDate }}</text>
                <ChevronRight :size="19" />
              </view>
            </picker>
          </view>
          <view v-if="!dateOnly" class="date-time-sheet__field">
            <text class="date-time-sheet__label">具体时间</text>
            <picker
              class="date-time-sheet__picker"
              mode="time"
              :value="draftTime"
              :start="minTime"
              :end="maxTime"
              @change="updateDraftTime"
            >
              <view class="date-time-sheet__picker-value" aria-label="选择具体时间">
                <text>{{ draftTime }}</text>
                <ChevronRight :size="19" />
              </view>
            </picker>
          </view>
        </view>

        <view v-if="draftError" class="date-time-sheet__error" role="alert">
          <AlertCircle :size="16" />
          <text>{{ draftError }}</text>
        </view>

        <view class="date-time-sheet__actions">
          <NButton size="large" secondary @click="closeSheet"><X :size="18" />取消</NButton>
          <NButton type="primary" size="large" @click="confirmDraft"><Check :size="18" />{{ dateOnly ? "确认日期" : "确认时间" }}</NButton>
        </view>
      </view>
    </NDrawer>
  </view>
</template>

<style scoped>
.date-time-field__desktop {
  width: 100%;
}

.date-time-field__mobile-trigger {
  display: none;
}

.date-time-sheet {
  box-sizing: border-box;
  display: flex;
  width: min(100%, 430px);
  height: 100%;
  margin: 0 auto;
  padding: 8px 16px calc(16px + env(safe-area-inset-bottom));
  flex-direction: column;
}

.date-time-sheet__handle {
  width: 38px;
  height: 4px;
  margin: 0 auto 12px;
  border-radius: 2px;
  background: var(--bud-color-line);
}

.date-time-sheet__header {
  margin-bottom: 13px;
  text-align: center;
}

.date-time-sheet__title,
.date-time-sheet__current,
.date-time-sheet__label {
  display: block;
}

.date-time-sheet__title {
  font-size: 18px;
  line-height: 26px;
  font-weight: 760;
}

.date-time-sheet__current {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.date-time-sheet__shortcuts,
.date-time-sheet__day-presets {
  display: grid;
  gap: 8px;
  margin-bottom: 13px;
}

.date-time-sheet__shortcuts {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.date-time-sheet__day-presets {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.date-time-sheet__shortcut,
.date-time-sheet__day-button {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  min-height: 46px;
  appearance: none;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 8px;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  color: var(--bud-color-ink);
  background: var(--bud-color-surface);
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  touch-action: manipulation;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease, transform 0.1s ease;
}

.date-time-sheet__shortcut--active,
.date-time-sheet__day-button--active {
  border-color: var(--bud-color-primary-dark);
  color: #fff;
  background: var(--bud-color-primary-dark);
  box-shadow: 0 2px 7px rgba(201, 54, 105, 0.2);
}

.date-time-sheet__shortcut:active,
.date-time-sheet__day-button:active {
  transform: scale(0.98);
}

.date-time-sheet__fields {
  display: grid;
  gap: 12px;
}

.date-time-sheet__label {
  margin-bottom: 6px;
  color: var(--bud-color-body);
  font-size: 13px;
  font-weight: 700;
}

.date-time-sheet__picker {
  display: block;
}

.date-time-sheet__picker-value {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  height: 50px;
  padding: 0 13px;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  color: var(--bud-color-ink);
  background: var(--bud-color-surface);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
}

.date-time-sheet__picker-value:active {
  border-color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
  box-shadow: 0 0 0 3px rgba(255, 93, 143, 0.12);
}

.date-time-sheet__picker-value .lucide {
  color: var(--bud-color-muted);
}

.date-time-sheet__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: var(--baby-danger);
  font-size: 12px;
  line-height: 18px;
}

.date-time-sheet__actions {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 10px;
  margin-top: auto;
  padding-top: 14px;
}

.date-time-sheet__actions :deep(.n-button) {
  height: 50px;
}

@media (max-width: 768px), (pointer: coarse) {
  .date-time-field__desktop {
    display: none;
  }

  .date-time-field__mobile-trigger {
    box-sizing: border-box;
    display: grid;
    width: 100%;
    min-height: 54px;
    appearance: none;
    margin: 0;
    padding: 0 12px 0 9px;
    grid-template-columns: 34px minmax(0, 1fr) auto;
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
    font-size: 16px;
    font-variant-numeric: tabular-nums;
    font-weight: 750;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .date-time-field__mobile-action {
    color: var(--bud-color-primary-dark);
    font-size: 13px;
    font-weight: 700;
  }
}

:deep(.date-time-drawer) {
  border-radius: 8px 8px 0 0;
}
</style>
