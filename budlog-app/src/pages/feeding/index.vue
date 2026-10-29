<script setup lang="ts">
import { ChevronDown, ChevronUp, History, Play, Save, SlidersHorizontal, Square, Timer, Trash2, X } from "lucide-vue-next";
import { NButton, NInput, NInputNumber } from "naive-ui";
import { onBackPress, onHide, onLoad, onShow, onUnload } from "@dcloudio/uni-app";
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import DateTimeField from "../../components/DateTimeField.vue";
import OptionalNoteField from "../../components/OptionalNoteField.vue";
import PageHeader from "../../components/PageHeader.vue";
import SegmentedControl from "../../components/SegmentedControl.vue";
import { useTimerStore } from "../../stores/timer";
import type { BreastSide, FeedingRecord, FeedingType } from "../../types";
import { formatDateTime, nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const timerStore = useTimerStore();
const recordId = ref<number>();
const mode = ref("");
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dirty = ref(false);
const moreOpen = ref(false);
const tick = ref(Date.now());
let hydrating = true;
let clock: ReturnType<typeof setInterval> | undefined;

const form = reactive({
  clientRequestId: uuid(),
  feedingType: "BREAST_DIRECT" as FeedingType,
  breastSide: "BOTH" as BreastSide | "",
  startTime: nowLocalInput(),
  durationMinutes: "",
  amountMl: "",
  note: "",
});

const isTimerPage = computed(() => mode.value === "timer" && !recordId.value);
const isDirect = computed(() => form.feedingType === "BREAST_DIRECT");
const isBottle = computed(() => form.feedingType !== "BREAST_DIRECT");
const elapsedSeconds = computed(() => timerStore.elapsedSeconds(tick.value));
const feedingTypeOptions = [
  { value: "BREAST_DIRECT", label: "亲喂" },
  { value: "BREAST_BOTTLE", label: "母乳瓶喂" },
  { value: "FORMULA_BOTTLE", label: "奶粉瓶喂" },
];
const availableFeedingTypeOptions = computed(() => (
  mode.value === "bottle" && !recordId.value
    ? feedingTypeOptions.filter((option) => option.value !== "BREAST_DIRECT")
    : feedingTypeOptions
));
const sideOptions = [
  { value: "LEFT", label: "左侧" },
  { value: "RIGHT", label: "右侧" },
  { value: "BOTH", label: "双侧" },
];
const optionalSideOptions = [{ value: "", label: "不选择" }, ...sideOptions];
const LAST_BOTTLE_AMOUNT_KEY = "budlog.feeding.lastBottleAmount";
const lastBottleAmount = ref<number>();
const lastBottleRecord = ref<FeedingRecord>();
const latestBottleLoadFailed = ref(false);
const amountPresets = [60, 90, 120];
const durationPresets = [5, 10, 15, 20];
const amountValue = computed<number | null>({
  get: () => form.amountMl === "" ? null : Number(form.amountMl),
  set: (value) => { form.amountMl = value === null ? "" : String(value); },
});
const durationValue = computed<number | null>({
  get: () => form.durationMinutes === "" ? null : Number(form.durationMinutes),
  set: (value) => { form.durationMinutes = value === null ? "" : String(value); },
});
const lastBottleSelected = computed(() => (
  lastBottleAmount.value !== undefined && Number(form.amountMl) === lastBottleAmount.value
));
const lastBottleContext = computed(() => {
  const record = lastBottleRecord.value;
  if (!record) return "本机保存的上次奶量";
  const type = record.feedingType === "FORMULA_BOTTLE" ? "奶粉瓶喂" : "母乳瓶喂";
  return `${type} · ${formatDateTime(record.startTime)}`;
});
const moreOptionsSummary = computed(() => {
  const details: string[] = [];
  if (form.feedingType === "BREAST_BOTTLE" && form.breastSide) {
    const label = sideOptions.find((option) => option.value === form.breastSide)?.label;
    if (label) details.push(label);
  }
  if (form.note.trim()) details.push("已填写备注");
  return details.length ? details.join(" · ") : "侧别、备注";
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const storedAmount = Number(uni.getStorageSync(LAST_BOTTLE_AMOUNT_KEY));
  if (Number.isFinite(storedAmount) && storedAmount > 0 && storedAmount <= 1000) lastBottleAmount.value = storedAmount;
  mode.value = String(options?.mode || "");
  if (mode.value === "bottle") {
    form.feedingType = "BREAST_BOTTLE";
    form.breastSide = "";
  }
  const id = Number(options?.id || 0);
  if (id) {
    recordId.value = id;
    await loadRecord(id);
  } else {
    if (mode.value !== "bottle" && timerStore.draft) form.breastSide = timerStore.draft.breastSide;
    if (!isTimerPage.value) {
      loading.value = true;
      try {
        await loadLatestBottle(mode.value === "bottle");
      } finally {
        loading.value = false;
      }
    }
  }
  await nextTick();
  hydrating = false;
  dirty.value = false;
});

onShow(startClock);
onHide(stopClock);
onUnload(stopClock);

onBackPress(() => {
  if (!dirty.value || saving.value) return false;
  uni.showModal({
    title: "放弃未保存内容",
    content: "当前修改尚未保存",
    success: (result) => {
      if (result.confirm) {
        dirty.value = false;
        uni.navigateBack();
      }
    },
  });
  return true;
});

function startClock() {
  stopClock();
  tick.value = Date.now();
  clock = setInterval(() => (tick.value = Date.now()), 1000);
}

function stopClock() {
  if (clock) clearInterval(clock);
  clock = undefined;
}

async function loadRecord(id: number) {
  loading.value = true;
  try {
    const record = await api.feeding(id);
    form.clientRequestId = record.clientRequestId;
    form.feedingType = record.feedingType;
    form.breastSide = record.breastSide || "";
    form.startTime = toLocalInput(record.startTime);
    form.durationMinutes = record.durationMinutes ? String(record.durationMinutes) : "";
    form.amountMl = record.amountMl === undefined ? "" : String(record.amountMl);
    form.note = record.note || "";
    moreOpen.value = record.feedingType !== "BREAST_DIRECT" && Boolean(record.breastSide || record.note);
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

function updateFeedingType(value: string | number) {
  const nextType = value as FeedingType;
  const previousType = form.feedingType;
  form.feedingType = nextType;

  if (nextType === "FORMULA_BOTTLE" || (nextType === "BREAST_BOTTLE" && previousType === "BREAST_DIRECT")) {
    form.breastSide = "";
  } else if (nextType === "BREAST_DIRECT" && !form.breastSide) {
    form.breastSide = "BOTH";
  }
  if (nextType !== "BREAST_DIRECT" && !form.amountMl && lastBottleAmount.value) useLastBottleAmount();
  if (nextType === "BREAST_DIRECT") moreOpen.value = false;
}

async function loadLatestBottle(prefill: boolean) {
  latestBottleLoadFailed.value = false;
  try {
    const record = await api.latestBottleFeeding();
    if (record?.amountMl && record.feedingType !== "BREAST_DIRECT") {
      lastBottleRecord.value = record;
      lastBottleAmount.value = Number(record.amountMl);
      uni.setStorageSync(LAST_BOTTLE_AMOUNT_KEY, lastBottleAmount.value);
      if (prefill) {
        form.feedingType = record.feedingType;
        form.breastSide = "";
        useLastBottleAmount();
      }
      return;
    }
    lastBottleRecord.value = undefined;
    lastBottleAmount.value = undefined;
    uni.removeStorageSync(LAST_BOTTLE_AMOUNT_KEY);
  } catch {
    latestBottleLoadFailed.value = true;
    if (prefill && lastBottleAmount.value) useLastBottleAmount();
  }
}

function startTimer() {
  if (!form.breastSide) return;
  timerStore.start(form.breastSide);
  dirty.value = false;
  tick.value = Date.now();
}

async function stopAndSave() {
  const draft = timerStore.draft;
  if (!draft || saving.value) return;
  saving.value = true;
  try {
    await api.createFeeding({
      clientRequestId: draft.clientRequestId,
      feedingType: "BREAST_DIRECT",
      breastSide: draft.breastSide,
      startTime: draft.startTime,
      endTime: new Date().toISOString(),
      note: form.note.trim() || undefined,
    });
    timerStore.clear();
    dirty.value = false;
    uni.showToast({ title: "已保存", icon: "success" });
    setTimeout(() => uni.navigateBack(), 350);
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  } finally {
    saving.value = false;
  }
}

function cancelTimer() {
  uni.showModal({
    title: "取消本次计时",
    content: "计时草稿将被清除",
    confirmColor: "#a43835",
    success: (result) => {
      if (result.confirm) timerStore.clear();
    },
  });
}

function validate(): string | undefined {
  if (isDirect.value && !form.breastSide) return "请选择亲喂侧别";
  if (isDirect.value && form.durationMinutes) {
    const duration = Number(form.durationMinutes);
    if (!Number.isInteger(duration) || duration < 1 || duration > 240) return "亲喂时长请输入 1 至 240 分钟";
    const endTime = new Date(toIso(form.startTime)).getTime() + duration * 60_000;
    if (endTime > Date.now() + 5 * 60_000) return "开始时间加亲喂时长不能晚于当前时间";
  }
  if (isBottle.value && (!form.amountMl || !Number.isFinite(Number(form.amountMl)) || Number(form.amountMl) <= 0)) {
    return "请输入有效的瓶喂奶量";
  }
  return undefined;
}

async function save() {
  if (saving.value) return;
  const message = validate();
  if (message) {
    uni.showToast({ title: message, icon: "none" });
    return;
  }
  saving.value = true;
  try {
    const payload = {
      clientRequestId: form.clientRequestId,
      feedingType: form.feedingType,
      breastSide: form.feedingType === "FORMULA_BOTTLE" || !form.breastSide ? undefined : form.breastSide,
      startTime: toIso(form.startTime),
      endTime: isDirect.value && form.durationMinutes ? endTimeForDuration() : undefined,
      amountMl: isBottle.value ? Number(form.amountMl) : undefined,
      note: form.note.trim() || undefined,
    };
    if (recordId.value) await api.updateFeeding(recordId.value, payload);
    else await api.createFeeding(payload);
    if (isBottle.value) {
      lastBottleAmount.value = Number(form.amountMl);
      uni.setStorageSync(LAST_BOTTLE_AMOUNT_KEY, lastBottleAmount.value);
    }
    dirty.value = false;
    uni.showToast({ title: "已保存", icon: "success" });
    setTimeout(() => uni.navigateBack(), 350);
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  } finally {
    saving.value = false;
  }
}

function remove() {
  if (!recordId.value || deleting.value) return;
  uni.showModal({
    title: "删除喂奶记录",
    content: "删除后首页统计会立即重算",
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !recordId.value) return;
      deleting.value = true;
      try {
        await api.deleteFeeding(recordId.value);
        dirty.value = false;
        uni.navigateBack();
      } catch (exception) {
        uni.showToast({ title: exception instanceof Error ? exception.message : "删除失败", icon: "none" });
      } finally {
        deleting.value = false;
      }
    },
  });
}

function formatTimer(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return [hours, minutes, rest].map((part) => String(part).padStart(2, "0")).join(":");
}

function setAmount(amount: number) {
  form.amountMl = String(amount);
}

function setDuration(minutes: number) {
  form.durationMinutes = String(minutes);
}

function endTimeForDuration(): string {
  const instant = new Date(toIso(form.startTime)).getTime() + Number(form.durationMinutes) * 60_000;
  return new Date(instant).toISOString();
}

function useLastBottleAmount() {
  if (lastBottleAmount.value) setAmount(lastBottleAmount.value);
}
</script>

<template>
  <AppPage>
    <view
      class="page-shell page-shell--form feeding-page"
      :class="{ 'feeding-page--quick-bottle': !recordId && isBottle }"
    >
    <PageHeader :title="recordId ? '编辑喂奶记录' : isTimerPage ? '亲喂计时' : mode === 'bottle' ? '记录瓶喂' : '记录喂奶'" back />

    <AppLoading v-if="loading" copy="正在加载喂奶记录" />

    <template v-else-if="isTimerPage">
      <view v-if="timerStore.draft" class="timer-panel surface">
        <view class="timer-panel__icon timer-panel__icon--active"><Timer :size="24" /></view>
        <text class="timer-panel__label">{{ timerStore.draft.breastSide === 'LEFT' ? '左侧' : timerStore.draft.breastSide === 'RIGHT' ? '右侧' : '双侧' }}</text>
        <text class="timer-panel__time">{{ formatTimer(elapsedSeconds) }}</text>
        <text class="timer-panel__started">开始于 {{ toLocalInput(timerStore.draft.startTime).slice(11) }}</text>
        <OptionalNoteField v-model="form.note" class="timer-note" />
        <view class="timer-actions">
          <NButton type="error" size="large" secondary block @click="cancelTimer"><X :size="18" />取消</NButton>
          <NButton type="primary" size="large" block :loading="saving" @click="stopAndSave"><Square :size="18" />{{ saving ? "保存中" : "停止并保存" }}</NButton>
        </view>
      </view>
      <view v-else class="timer-panel surface">
        <view class="timer-panel__icon"><Timer :size="24" /></view>
        <text class="timer-panel__prompt">选择亲喂侧别</text>
        <text class="timer-panel__copy">计时会在离开页面后继续保留</text>
        <SegmentedControl v-model="form.breastSide" class="timer-side" :options="sideOptions" />
        <NButton type="primary" size="large" block :disabled="!form.breastSide" @click="startTimer"><Play :size="19" />开始计时</NButton>
      </view>
    </template>

    <view v-else-if="!loading" class="feeding-form surface">
      <view class="field">
        <text class="field__label">喂奶类型</text>
        <SegmentedControl :model-value="form.feedingType" :options="availableFeedingTypeOptions" @update:model-value="updateFeedingType" />
      </view>

      <view v-if="isDirect" class="field">
        <text class="field__label">侧别</text>
        <SegmentedControl v-model="form.breastSide" :options="sideOptions" />
      </view>

      <view class="field">
        <text class="field__label">开始时间</text>
        <DateTimeField v-model="form.startTime" title="选择开始时间" quick-record :max-now-offset-minutes="5" />
      </view>

      <template v-if="isDirect">
        <view class="field">
          <text class="field__label">亲喂时长（分钟，可选）</text>
          <view class="amount-stepper">
            <NInputNumber
              v-model:value="durationValue"
              size="large"
              :min="1"
              :max="240"
              :step="5"
              :precision="0"
              button-placement="both"
              placeholder="未记录"
            />
            <text class="amount-stepper__unit">分钟</text>
          </view>
          <view class="duration-presets shortcut-row">
            <button
              v-for="minutes in durationPresets"
              :key="minutes"
              class="shortcut-chip duration-preset"
              :class="{ 'shortcut-chip--active': Number(form.durationMinutes) === minutes }"
              @click="setDuration(minutes)"
            >
              {{ minutes }} 分钟
            </button>
          </view>
        </view>
      </template>

      <view v-if="isBottle" class="field">
        <text class="field__label">奶量（ml）</text>
        <button
          v-if="lastBottleAmount && !recordId"
          class="last-bottle-action"
          :class="{ 'last-bottle-action--active': lastBottleSelected }"
          :aria-pressed="lastBottleSelected"
          hover-class="none"
          @click="useLastBottleAmount"
        >
          <History :size="19" />
          <view class="last-bottle-action__body">
            <text class="last-bottle-action__title">上次 {{ lastBottleAmount }} ml</text>
            <text class="last-bottle-action__meta">{{ lastBottleContext }}</text>
          </view>
          <text class="last-bottle-action__state">{{ lastBottleSelected ? "已沿用" : "填入" }}</text>
        </button>
        <text v-if="latestBottleLoadFailed" class="last-bottle-sync-hint" role="status">
          {{ lastBottleAmount ? "家庭记录同步失败，当前使用本机记录" : "未能读取家庭上次奶量，请手动填写" }}
        </text>
        <view class="amount-stepper">
          <NInputNumber
            v-model:value="amountValue"
            size="large"
            :min="1"
            :max="1000"
            :step="10"
            :precision="0"
            button-placement="both"
            placeholder="0"
          />
          <text class="amount-stepper__unit">ml</text>
        </view>
        <view v-if="!lastBottleAmount" class="amount-presets shortcut-row">
          <button
            v-for="amount in amountPresets"
            :key="amount"
            class="shortcut-chip amount-preset"
            :class="{ 'shortcut-chip--active': Number(form.amountMl) === amount }"
            @click="setAmount(amount)"
          >
            {{ amount }}
          </button>
        </view>
      </view>

      <OptionalNoteField v-if="isDirect" v-model="form.note" />
      <template v-else>
        <button
          class="more-options-toggle"
          :aria-expanded="moreOpen"
          hover-class="none"
          @click="moreOpen = !moreOpen"
        >
          <SlidersHorizontal :size="19" />
          <view class="more-options-toggle__body">
            <text class="more-options-toggle__title">更多选项</text>
            <text class="more-options-toggle__summary">{{ moreOptionsSummary }}</text>
          </view>
          <ChevronUp v-if="moreOpen" :size="19" />
          <ChevronDown v-else :size="19" />
        </button>
        <view v-if="moreOpen" class="more-options-panel">
          <view v-if="form.feedingType === 'BREAST_BOTTLE'" class="field">
            <text class="field__label">侧别（可选）</text>
            <SegmentedControl v-model="form.breastSide" :options="optionalSideOptions" />
          </view>
          <view class="field more-options-note">
            <text class="field__label">备注（可选）</text>
            <NInput v-model:value="form.note" type="textarea" :maxlength="500" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="可选" />
          </view>
        </view>
      </template>

      <view
        class="form-actions feeding-actions"
        :class="{ 'feeding-actions--quick': !recordId && isBottle }"
      >
        <NButton v-if="recordId" type="error" size="large" secondary block :loading="deleting" @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : recordId ? "保存修改" : isBottle ? "保存本次瓶喂" : "保存喂奶记录" }}</NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.feeding-form,
.timer-panel {
  padding: 16px;
}

.amount-presets,
.duration-presets {
  display: grid;
  gap: 7px;
  margin-top: 8px;
}

.amount-presets {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.duration-presets {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.amount-preset,
.duration-preset {
  width: 100%;
  min-height: 46px;
  min-width: 0;
  padding-right: 8px;
  padding-left: 8px;
}

.last-bottle-action {
  box-sizing: border-box;
  display: grid;
  width: 100%;
  min-height: 54px;
  margin: 0 0 8px;
  padding: 7px 11px;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  border: 1px solid #cfe1fb;
  border-radius: 8px;
  color: #246bb8;
  background: #f2f7ff;
  text-align: left;
  touch-action: manipulation;
}

.last-bottle-action--active {
  border-color: #8fb9ef;
  background: #e8f2ff;
}

.last-bottle-action__body {
  min-width: 0;
}

.last-bottle-action__title,
.last-bottle-action__meta {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-bottle-action__title {
  color: var(--bud-color-ink);
  font-size: 14px;
  line-height: 20px;
  font-weight: 780;
}

.last-bottle-action__meta {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 11px;
  line-height: 17px;
}

.last-bottle-action__state {
  color: #246bb8;
  font-size: 12px;
  font-weight: 750;
}

.last-bottle-sync-hint {
  display: block;
  margin: -2px 0 10px;
  color: var(--baby-danger);
  font-size: 12px;
  line-height: 18px;
}

.amount-stepper {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--bud-color-line);
  border-radius: 10px;
  background: var(--bud-color-surface);
}

.amount-stepper__unit {
  color: var(--bud-color-muted);
  font-size: 14px;
  font-weight: 650;
}

.more-options-toggle {
  box-sizing: border-box;
  display: grid;
  width: 100%;
  min-height: 54px;
  margin: 0 0 20px;
  padding: 7px 12px;
  grid-template-columns: 22px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--bud-color-line-soft);
  border-radius: 8px;
  color: var(--bud-color-body);
  background: var(--bud-color-surface);
  text-align: left;
}

.more-options-toggle > .lucide:first-child {
  color: var(--bud-color-primary);
}

.more-options-toggle > .lucide:last-child {
  color: var(--bud-color-muted);
}

.more-options-toggle__body,
.more-options-toggle__title,
.more-options-toggle__summary {
  display: block;
  min-width: 0;
}

.more-options-toggle__title {
  font-size: 14px;
  font-weight: 750;
}

.more-options-toggle__summary {
  margin-top: 1px;
  overflow: hidden;
  color: var(--bud-color-muted);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-options-panel {
  margin: -10px 0 20px;
  padding: 14px 12px 0;
  border: 1px solid var(--bud-color-line-soft);
  border-radius: 8px;
  background: var(--bud-color-canvas);
}

.more-options-note {
  margin-bottom: 14px;
}

.timer-panel {
  min-height: 360px;
  padding-top: 28px;
  text-align: center;
}

.timer-panel__label,
.timer-panel__time,
.timer-panel__started,
.timer-panel__prompt,
.timer-panel__copy {
  display: block;
}

.timer-panel__icon {
  display: flex;
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.timer-panel__icon--active {
  color: #ffffff;
  background: var(--bud-color-primary);
  box-shadow: 0 8px 18px rgba(255, 93, 143, 0.22);
}

.timer-panel__label {
  color: var(--bud-color-primary);
  font-weight: 700;
}

.timer-panel__time {
  margin: 16px 0 5px;
  font-size: 40px;
  line-height: 50px;
  font-weight: 780;
  font-variant-numeric: tabular-nums;
}

.timer-panel__started {
  color: var(--bud-color-muted);
  font-size: 13px;
}

.timer-note {
  margin-top: 24px;
  text-align: left;
}

.timer-actions {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.timer-panel__prompt {
  margin: 4px 0 2px;
  font-size: 18px;
  font-weight: 720;
}

.timer-panel__copy {
  margin-bottom: 22px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.timer-side {
  margin-bottom: 20px;
}

@media (max-width: 360px) {
  .timer-actions {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px), (pointer: coarse) {
  .feeding-page--quick-bottle {
    padding-bottom: calc(108px + env(safe-area-inset-bottom));
  }

  .feeding-page--quick-bottle .feeding-form {
    animation: none;
    transform: none;
  }

  .feeding-actions--quick {
    box-sizing: border-box;
    position: fixed;
    bottom: 0;
    left: 50%;
    z-index: 30;
    width: min(430px, 100%);
    margin: 0;
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    background: var(--bud-color-canvas);
    box-shadow: 0 -8px 20px rgba(69, 80, 106, 0.1);
    transform: translateX(-50%);
  }

  .feeding-actions--quick :deep(.n-button) {
    height: 52px;
  }
}
</style>
