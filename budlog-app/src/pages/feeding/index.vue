<script setup lang="ts">
import { Play, Save, Square, Timer, Trash2, X } from "lucide-vue-next";
import { NButton, NInput, NInputNumber, NSwitch } from "naive-ui";
import { onBackPress, onHide, onLoad, onShow, onUnload } from "@dcloudio/uni-app";
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import DateTimeField from "../../components/DateTimeField.vue";
import PageHeader from "../../components/PageHeader.vue";
import SegmentedControl from "../../components/SegmentedControl.vue";
import { useTimerStore } from "../../stores/timer";
import type { BreastSide, FeedingType } from "../../types";
import { nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const timerStore = useTimerStore();
const recordId = ref<number>();
const mode = ref("");
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dirty = ref(false);
const tick = ref(Date.now());
let hydrating = true;
let clock: ReturnType<typeof setInterval> | undefined;

const form = reactive({
  clientRequestId: uuid(),
  feedingType: "BREAST_DIRECT" as FeedingType,
  breastSide: "LEFT" as BreastSide | "",
  startTime: nowLocalInput(),
  endTime: nowLocalInput(),
  hasEnd: true,
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
const sideOptions = [
  { value: "LEFT", label: "左侧" },
  { value: "RIGHT", label: "右侧" },
  { value: "BOTH", label: "双侧" },
];
const optionalSideOptions = [{ value: "", label: "不选择" }, ...sideOptions];
const LAST_BOTTLE_AMOUNT_KEY = "budlog.feeding.lastBottleAmount";
const lastBottleAmount = ref<number>();
const amountPresets = computed(() => {
  const values = [30, 60, 90, 120, 150, 180];
  const options = values.map((value) => ({ value, label: String(value) }));
  if (lastBottleAmount.value && !values.includes(lastBottleAmount.value)) {
    options.unshift({ value: lastBottleAmount.value, label: `上次 ${lastBottleAmount.value}` });
  }
  return options;
});
const amountValue = computed<number | null>({
  get: () => form.amountMl === "" ? null : Number(form.amountMl),
  set: (value) => { form.amountMl = value === null ? "" : String(value); },
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const storedAmount = Number(uni.getStorageSync(LAST_BOTTLE_AMOUNT_KEY));
  if (Number.isFinite(storedAmount) && storedAmount > 0 && storedAmount <= 1000) lastBottleAmount.value = storedAmount;
  mode.value = String(options?.mode || "");
  if (mode.value === "bottle") form.feedingType = "BREAST_BOTTLE";
  const id = Number(options?.id || 0);
  if (id) {
    recordId.value = id;
    await loadRecord(id);
  } else if (timerStore.draft) {
    form.breastSide = timerStore.draft.breastSide;
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
    form.breastSide = record.breastSide || (record.feedingType === "BREAST_DIRECT" ? "LEFT" : "");
    form.startTime = toLocalInput(record.startTime);
    form.endTime = toLocalInput(record.endTime || record.startTime);
    form.hasEnd = Boolean(record.endTime);
    form.amountMl = record.amountMl === undefined ? "" : String(record.amountMl);
    form.note = record.note || "";
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
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
  if (isBottle.value && (!form.amountMl || !Number.isFinite(Number(form.amountMl)) || Number(form.amountMl) <= 0)) {
    return "请输入有效的瓶喂奶量";
  }
  if (form.hasEnd && new Date(toIso(form.endTime)).getTime() < new Date(toIso(form.startTime)).getTime()) return "结束时间不能早于开始时间";
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
      endTime: isDirect.value && form.hasEnd ? toIso(form.endTime) : undefined,
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
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form feeding-page">
    <PageHeader :title="recordId ? '编辑喂奶记录' : isTimerPage ? '亲喂计时' : '记录喂奶'" back />

    <AppLoading v-if="loading" copy="正在加载喂奶记录" />

    <template v-else-if="isTimerPage">
      <view v-if="timerStore.draft" class="timer-panel surface">
        <view class="timer-panel__icon timer-panel__icon--active"><Timer :size="24" /></view>
        <text class="timer-panel__label">{{ timerStore.draft.breastSide === 'LEFT' ? '左侧' : timerStore.draft.breastSide === 'RIGHT' ? '右侧' : '双侧' }}</text>
        <text class="timer-panel__time">{{ formatTimer(elapsedSeconds) }}</text>
        <text class="timer-panel__started">开始于 {{ toLocalInput(timerStore.draft.startTime).slice(11) }}</text>
        <view class="field timer-note">
          <text class="field__label">备注</text>
          <NInput v-model:value="form.note" type="textarea" :maxlength="500" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="可选" />
        </view>
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
        <SegmentedControl v-model="form.feedingType" :options="feedingTypeOptions" />
      </view>

      <view v-if="form.feedingType !== 'FORMULA_BOTTLE'" class="field">
        <text class="field__label">侧别{{ isDirect ? '' : '（可选）' }}</text>
        <SegmentedControl v-model="form.breastSide" :options="isDirect ? sideOptions : optionalSideOptions" />
      </view>

      <view class="field">
        <text class="field__label">开始时间</text>
        <DateTimeField v-model="form.startTime" title="选择开始时间" quick-record :max-now-offset-minutes="5" />
      </view>

      <template v-if="isDirect">
        <view class="toggle-end">
          <view>
            <text class="toggle-end__title">填写结束时间</text>
            <text class="toggle-end__copy">关闭后仅保存开始时间</text>
          </view>
          <NSwitch v-model:value="form.hasEnd" />
        </view>
        <view v-if="form.hasEnd" class="field">
          <text class="field__label">结束时间</text>
          <DateTimeField v-model="form.endTime" title="选择结束时间" quick-record :max-now-offset-minutes="5" />
        </view>
      </template>

      <view v-if="isBottle" class="field">
        <text class="field__label">奶量（ml）</text>
        <view class="amount-stepper">
          <NInputNumber
            v-model:value="amountValue"
            :min="1"
            :max="1000"
            :step="10"
            :precision="0"
            button-placement="both"
            placeholder="0"
          />
          <text class="amount-stepper__unit">ml</text>
        </view>
        <view class="amount-presets shortcut-row">
          <button
            v-for="option in amountPresets"
            :key="option.value"
            class="shortcut-chip amount-preset"
            :class="{ 'shortcut-chip--active': Number(form.amountMl) === option.value }"
            @click="setAmount(option.value)"
          >
            {{ option.label }}
          </button>
        </view>
        <text v-if="lastBottleAmount" class="field__hint">上次记录 {{ lastBottleAmount }} ml</text>
      </view>

      <view class="field">
        <text class="field__label">备注</text>
        <NInput v-model:value="form.note" type="textarea" :maxlength="500" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" />
      </view>

      <view class="form-actions feeding-actions">
        <NButton v-if="recordId" type="error" size="large" secondary block :loading="deleting" @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : "保存" }}</NButton>
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

.toggle-end {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  gap: 16px;
  margin-bottom: 14px;
  border-top: 1px solid var(--bud-color-line-soft);
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.toggle-end__title,
.toggle-end__copy {
  display: block;
}

.toggle-end__title {
  font-size: 14px;
  font-weight: 650;
}

.toggle-end__copy {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.amount-presets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  margin-top: 8px;
}

.amount-preset {
  width: 100%;
  min-width: 0;
  padding-right: 8px;
  padding-left: 8px;
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
</style>
