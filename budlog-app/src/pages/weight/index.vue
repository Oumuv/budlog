<script setup lang="ts">
import { Save, Trash2 } from "lucide-vue-next";
import { onBackPress, onLoad } from "@dcloudio/uni-app";
import { nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import DateTimeField from "../../components/DateTimeField.vue";
import PageHeader from "../../components/PageHeader.vue";
import type { WeightRecord } from "../../types";
import { nowLocalInput, shiftDay, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const recordId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dirty = ref(false);
let hydrating = true;

const form = reactive({
  clientRequestId: uuid(),
  measuredAt: nowLocalInput(),
  weightKg: "",
  note: "",
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const requestedDate = String(options?.date || "");
  if (/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) {
    form.measuredAt = `${requestedDate}T${nowLocalInput().slice(11)}`;
  }
  const id = Number(options?.id || 0);
  if (id) {
    await loadRecord(id);
  } else {
    await loadExistingForDate(form.measuredAt.slice(0, 10));
  }
  await nextTick();
  hydrating = false;
  dirty.value = false;
});

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

function hydrate(record: WeightRecord) {
  recordId.value = record.id;
  form.clientRequestId = record.clientRequestId;
  form.measuredAt = toLocalInput(record.measuredAt);
  form.weightKg = String(record.weightKg);
  form.note = record.note || "";
}

async function loadRecord(id: number) {
  loading.value = true;
  try {
    hydrate(await api.weightRecord(id));
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

async function loadExistingForDate(date: string) {
  loading.value = true;
  try {
    const records = await api.weightRecords(date, shiftDay(date, 1));
    if (records.content.length) hydrate(records.content[0]);
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

function validate(): string | undefined {
  const weight = Number(form.weightKg);
  if (!form.weightKg || !Number.isFinite(weight) || weight < 0.1 || weight > 100) {
    return "请输入 0.100 至 100.000 kg 的体重";
  }
  if (Math.abs(weight * 1000 - Math.round(weight * 1000)) > 1e-8) {
    return "体重最多保留 3 位小数";
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
    if (!recordId.value) {
      const date = form.measuredAt.slice(0, 10);
      const records = await api.weightRecords(date, shiftDay(date, 1));
      if (records.content.length) {
        recordId.value = records.content[0].id;
        form.clientRequestId = records.content[0].clientRequestId;
      }
    }
    const payload = {
      clientRequestId: form.clientRequestId,
      measuredAt: toIso(form.measuredAt),
      weightKg: Number(form.weightKg),
      note: form.note.trim() || undefined,
    };
    if (recordId.value) await api.updateWeight(recordId.value, payload);
    else await api.createWeight(payload);
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
    title: "删除体重记录",
    content: "删除后当天摘要和时间线会立即更新",
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !recordId.value) return;
      deleting.value = true;
      try {
        await api.deleteWeight(recordId.value);
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
</script>

<template>
  <view class="page-shell page-shell--form weight-page">
    <PageHeader :title="recordId ? '编辑体重记录' : '记录体重'" back />

    <view v-if="loading" class="state-panel surface">
      <wd-loading color="#b94b5d" />
      <text class="state-panel__copy">正在加载体重记录</text>
    </view>

    <view v-else class="weight-form surface">
      <view class="field">
        <text class="field__label">测量时间</text>
        <DateTimeField v-model="form.measuredAt" title="选择测量时间" quick-record :max-now-offset-minutes="5" />
      </view>

      <view class="field">
        <text class="field__label">体重（kg）</text>
        <view class="weight-stepper">
          <wd-input-number
            v-model="form.weightKg"
            :min="0.1"
            :max="100"
            :step="0.01"
            :precision="3"
            allow-null
            long-press
            input-type="digit"
            placeholder="0.000"
          />
          <text class="weight-stepper__unit">kg</text>
        </view>
      </view>

      <view class="field">
        <text class="field__label">备注</text>
        <wd-textarea v-model="form.note" custom-class="wot-control" no-border :maxlength="500" placeholder="可选" />
      </view>

      <view class="wot-action-row">
        <wd-button v-if="recordId" :round="false" type="error" size="large" plain block :loading="deleting" @click="remove"><Trash2 :size="18" />删除</wd-button>
        <wd-button :round="false" type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : recordId ? "保存修改" : "记录体重" }}</wd-button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.weight-form {
  padding: 16px;
}

.weight-stepper {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  background: #fffdfd;
}

.weight-stepper__unit {
  color: var(--bud-color-muted);
  font-size: 14px;
  font-weight: 650;
}
</style>
