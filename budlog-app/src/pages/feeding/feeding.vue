<script setup lang="ts">
import { Play, Save, Square, Trash2, X } from "lucide-vue-next";
import { onBackPress, onHide, onLoad, onShow, onUnload } from "@dcloudio/uni-app";
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import DateTimeField from "../../components/DateTimeField.vue";
import PageHeader from "../../components/PageHeader.vue";
import { useTimerStore } from "../../stores/timer";
import type { BreastSide, FeedingType } from "../../types";
import { nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { switchValue } from "../../utils/events";
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

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
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
  if (form.hasEnd && new Date(form.endTime).getTime() < new Date(form.startTime).getTime()) return "结束时间不能早于开始时间";
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
</script>

<template>
  <view class="page-shell page-shell--form feeding-page">
    <PageHeader :title="recordId ? '编辑喂奶记录' : isTimerPage ? '亲喂计时' : '记录喂奶'" back />

    <view v-if="loading" class="surface empty-state">加载中</view>

    <template v-else-if="isTimerPage">
      <view v-if="timerStore.draft" class="timer-panel surface">
        <text class="timer-panel__label">{{ timerStore.draft.breastSide === 'LEFT' ? '左侧' : timerStore.draft.breastSide === 'RIGHT' ? '右侧' : '双侧' }}</text>
        <text class="timer-panel__time">{{ formatTimer(elapsedSeconds) }}</text>
        <text class="timer-panel__started">开始于 {{ toLocalInput(timerStore.draft.startTime).slice(11) }}</text>
        <view class="field timer-note">
          <text class="field__label">备注</text>
          <textarea v-model="form.note" class="field__control" maxlength="500" placeholder="可选" />
        </view>
        <view class="timer-actions">
          <button class="btn btn--danger" @click="cancelTimer"><X :size="18" />取消</button>
          <button class="btn btn--primary" :disabled="saving" @click="stopAndSave"><Square :size="18" />{{ saving ? "保存中" : "停止并保存" }}</button>
        </view>
      </view>
      <view v-else class="timer-panel surface">
        <text class="timer-panel__prompt">选择亲喂侧别</text>
        <view class="segmented timer-side">
          <view v-for="side in [{ key: 'LEFT', label: '左侧' }, { key: 'RIGHT', label: '右侧' }, { key: 'BOTH', label: '双侧' }]" :key="side.key" class="segmented__item" :class="{ 'segmented__item--active': form.breastSide === side.key }" @click="form.breastSide = side.key as BreastSide">{{ side.label }}</view>
        </view>
        <button class="btn btn--primary timer-start" @click="startTimer"><Play :size="19" />开始计时</button>
      </view>
    </template>

    <view v-else-if="!loading" class="feeding-form surface">
      <view class="field">
        <text class="field__label">喂奶类型</text>
        <view class="segmented type-segmented">
          <view v-for="type in [{ key: 'BREAST_DIRECT', label: '亲喂' }, { key: 'BREAST_BOTTLE', label: '母乳瓶喂' }, { key: 'FORMULA_BOTTLE', label: '奶粉瓶喂' }]" :key="type.key" class="segmented__item" :class="{ 'segmented__item--active': form.feedingType === type.key }" @click="form.feedingType = type.key as FeedingType">{{ type.label }}</view>
        </view>
      </view>

      <view v-if="form.feedingType !== 'FORMULA_BOTTLE'" class="field">
        <text class="field__label">侧别{{ isDirect ? '' : '（可选）' }}</text>
        <view class="segmented">
          <view v-if="!isDirect" class="segmented__item" :class="{ 'segmented__item--active': !form.breastSide }" @click="form.breastSide = ''">不选择</view>
          <view v-for="side in [{ key: 'LEFT', label: '左侧' }, { key: 'RIGHT', label: '右侧' }, { key: 'BOTH', label: '双侧' }]" :key="side.key" class="segmented__item" :class="{ 'segmented__item--active': form.breastSide === side.key }" @click="form.breastSide = side.key as BreastSide">{{ side.label }}</view>
        </view>
      </view>

      <view class="field">
        <text class="field__label">开始时间</text>
        <DateTimeField v-model="form.startTime" />
      </view>

      <template v-if="isDirect">
        <view class="toggle-end">
          <text>填写结束时间</text>
          <switch :checked="form.hasEnd" color="#216454" @change="form.hasEnd = switchValue($event)" />
        </view>
        <view v-if="form.hasEnd" class="field">
          <text class="field__label">结束时间</text>
          <DateTimeField v-model="form.endTime" />
        </view>
      </template>

      <view v-if="isBottle" class="field">
        <text class="field__label">奶量（ml）</text>
        <input v-model="form.amountMl" class="field__control" type="digit" placeholder="请输入奶量" />
        <view class="amount-presets">
          <button v-for="amount in [30, 60, 90, 120]" :key="amount" class="amount-preset" @click="form.amountMl = String(amount)">{{ amount }}</button>
        </view>
      </view>

      <view class="field">
        <text class="field__label">备注</text>
        <textarea v-model="form.note" class="field__control" maxlength="500" placeholder="可选" />
      </view>

      <view class="form-actions">
        <button v-if="recordId" class="btn btn--danger" :disabled="deleting" @click="remove"><Trash2 :size="18" />删除</button>
        <button class="btn btn--primary" :disabled="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : "保存" }}</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.feeding-form,
.timer-panel {
  padding: 16px;
}

.type-segmented {
  grid-auto-flow: row;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.toggle-end {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  margin-bottom: 12px;
  border-top: 1px solid #edf0ee;
}

.amount-presets {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
  margin-top: 8px;
}

.amount-preset {
  min-width: 0;
  height: 36px;
  margin: 0;
  padding: 0;
  border: 1px solid #cad7d1;
  border-radius: 5px;
  color: #216454;
  background: #f6faf8;
  font-size: 13px;
}

.timer-panel {
  text-align: center;
}

.timer-panel__label,
.timer-panel__time,
.timer-panel__started,
.timer-panel__prompt {
  display: block;
}

.timer-panel__label {
  color: #216454;
  font-weight: 700;
}

.timer-panel__time {
  margin: 18px 0 5px;
  font-size: 42px;
  line-height: 52px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.timer-panel__started {
  color: #74807b;
  font-size: 13px;
}

.timer-note {
  margin-top: 24px;
  text-align: left;
}

.timer-actions {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.timer-panel__prompt {
  margin: 8px 0 18px;
  font-size: 18px;
  font-weight: 700;
}

.timer-side {
  margin-bottom: 18px;
}

.timer-start {
  width: 100%;
}
</style>
