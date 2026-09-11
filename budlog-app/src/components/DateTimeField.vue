<script setup lang="ts">
import { AlertCircle, CalendarClock, Check, ChevronRight, X } from "lucide-vue-next";
import { NButton, NDatePicker, NDrawer } from "naive-ui";
import { computed, ref } from "vue";
import { nowLocalInput, shiftDay, toIso, toLocalInput, todayKey } from "../utils/date";

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
const sheetOpen = ref(false);
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
  sheetOpen.value = false;
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

function openSheet() {
  refreshCurrentBoundary();
  const value = validLocalValue(props.modelValue) || nowLocalInput();
  draftDate.value = value.slice(0, 10);
  draftTime.value = value.slice(11, 16);
  draftError.value = "";
  sheetOpen.value = true;
}

function closeSheet() {
  sheetOpen.value = false;
  draftError.value = "";
}

function selectDraftDay(value: string) {
  draftDate.value = value;
  draftError.value = "";
}

function updateDraftDate(event: { detail: { value: string } }) {
  selectDraftDay(event.detail.value);
}

function updateDraftTime(event: { detail: { value: string } }) {
  draftTime.value = event.detail.value;
  draftError.value = "";
}

function confirmDraft() {
  refreshCurrentBoundary();
  const value = `${draftDate.value}T${draftTime.value}`;
  try {
    const instant = new Date(toIso(value)).getTime();
    if (instant < resolvedMinDate.value) {
      draftError.value = `不能早于${formatBoundary(resolvedMinLocal.value)}`;
      return;
    }
    if (instant > resolvedMaxDate.value) {
      draftError.value = props.maxNowOffsetMinutes !== undefined
        ? "所选时间不能晚于当前允许时间"
        : `不能晚于${formatBoundary(resolvedMaxLocal.value)}`;
      return;
    }
    emit("update:modelValue", value);
    closeSheet();
  } catch (exception) {
    draftError.value = exception instanceof Error ? exception.message : "请选择有效的日期和时间";
  }
}

function validLocalValue(value: string): string | undefined {
  try {
    toIso(value);
    return value;
  } catch {
    return undefined;
  }
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
      type="datetime"
      :placeholder="title"
      :min-date="resolvedMinDate"
      :max-date="resolvedMaxDate"
      :clearable="false"
      format="yyyy-MM-dd HH:mm"
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
      <ChevronRight class="date-time-field__mobile-arrow" :size="19" />
    </button>
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

    <NDrawer
      v-model:show="sheetOpen"
      class="date-time-drawer"
      placement="bottom"
      height="min(470px, calc(100dvh - 12px))"
      :z-index="800"
      :auto-focus="false"
    >
      <view class="date-time-sheet">
        <view class="date-time-sheet__handle" />
        <view class="date-time-sheet__header">
          <text class="date-time-sheet__title">{{ title }}</text>
          <text class="date-time-sheet__current">{{ displayValue }}</text>
        </view>

        <view v-if="quickRecord" class="date-time-sheet__day-presets">
          <button
            class="date-time-sheet__day-button"
            :class="{ 'date-time-sheet__day-button--active': draftDate === todayKey() }"
            :aria-pressed="draftDate === todayKey()"
            hover-class="none"
            @click="selectDraftDay(todayKey())"
          >今天</button>
          <button
            class="date-time-sheet__day-button"
            :class="{ 'date-time-sheet__day-button--active': draftDate === shiftDay(todayKey(), -1) }"
            :aria-pressed="draftDate === shiftDay(todayKey(), -1)"
            hover-class="none"
            @click="selectDraftDay(shiftDay(todayKey(), -1))"
          >昨天</button>
        </view>

        <view class="date-time-sheet__fields">
          <view class="date-time-sheet__field">
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
          <view class="date-time-sheet__field">
            <text class="date-time-sheet__label">时间</text>
            <picker
              class="date-time-sheet__picker"
              mode="time"
              :value="draftTime"
              :start="minTime"
              :end="maxTime"
              @change="updateDraftTime"
            >
              <view class="date-time-sheet__picker-value" aria-label="选择时间">
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
          <NButton type="primary" size="large" @click="confirmDraft"><Check :size="18" />确认时间</NButton>
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

.date-time-field__shortcuts {
  display: none;
  gap: 7px;
  margin-top: 9px;
}

.date-time-field__shortcut,
.date-time-sheet__day-button {
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

.date-time-field__shortcut--active,
.date-time-sheet__day-button--active {
  border-color: var(--bud-color-primary-dark);
  color: #ffffff;
  background: var(--bud-color-primary-dark);
  box-shadow: 0 2px 7px rgba(201, 54, 105, 0.2);
}

.date-time-field__shortcut:active:not(.date-time-field__shortcut--active),
.date-time-sheet__day-button:active:not(.date-time-sheet__day-button--active) {
  border-color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
  transform: scale(0.98);
}

.date-time-field__shortcut--active:active,
.date-time-sheet__day-button--active:active {
  border-color: var(--baby-primary-pressed);
  background: var(--baby-primary-pressed);
  transform: scale(0.98);
}

.date-time-field__shortcut:focus-visible,
.date-time-sheet__day-button:focus-visible {
  outline: 2px solid rgba(255, 93, 143, 0.32);
  outline-offset: 2px;
}

.date-time-sheet {
  box-sizing: border-box;
  display: flex;
  width: min(100%, 720px);
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
  margin-bottom: 14px;
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

.date-time-sheet__day-presets {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.date-time-sheet__day-button {
  min-height: 44px;
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
  height: 48px;
}

@media (max-width: 768px), (pointer: coarse) {
  .date-time-field__desktop {
    display: none;
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

:deep(.date-time-drawer) {
  border-radius: 12px 12px 0 0;
}
</style>
