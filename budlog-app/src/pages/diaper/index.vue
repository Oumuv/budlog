<script setup lang="ts">
import { Save, Trash2 } from "lucide-vue-next";
import { NButton, NInput } from "naive-ui";
import { onBackPress, onLoad } from "@dcloudio/uni-app";
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import DateTimeField from "../../components/DateTimeField.vue";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import PageHeader from "../../components/PageHeader.vue";
import SegmentedControl from "../../components/SegmentedControl.vue";
import type { DiaperType } from "../../types";
import { nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const recordId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const dirty = ref(false);
let hydrating = true;

const form = reactive({
  clientRequestId: uuid(),
  recordType: "PEE" as DiaperType,
  recordTime: nowLocalInput(),
  note: "",
});
const recordTypeOptions = [
  { value: "PEE", label: "尿尿" },
  { value: "POOP", label: "便便" },
  { value: "BOTH", label: "尿便都有" },
];
const recordTypeLabel = computed(() => {
  if (form.recordType === "PEE") return "尿尿";
  if (form.recordType === "POOP") return "便便";
  return "尿便";
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const requestedType = String(options?.type || "");
  if (["PEE", "POOP", "BOTH"].includes(requestedType)) form.recordType = requestedType as DiaperType;
  const id = Number(options?.id || 0);
  if (id) {
    recordId.value = id;
    loading.value = true;
    try {
      const record = await api.diaper(id);
      form.clientRequestId = record.clientRequestId;
      form.recordType = record.recordType;
      form.recordTime = toLocalInput(record.recordTime);
      form.note = record.note || "";
    } catch (exception) {
      uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
    } finally {
      loading.value = false;
    }
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

async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    const payload = {
      clientRequestId: form.clientRequestId,
      recordType: form.recordType,
      recordTime: toIso(form.recordTime),
      note: form.note.trim() || undefined,
    };
    if (recordId.value) await api.updateDiaper(recordId.value, payload);
    else await api.createDiaper(payload);
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
  if (!recordId.value) return;
  uni.showModal({
    title: "删除尿便记录",
    content: "删除后首页统计会立即重算",
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !recordId.value) return;
      try {
        await api.deleteDiaper(recordId.value);
        dirty.value = false;
        uni.navigateBack();
      } catch (exception) {
        uni.showToast({ title: exception instanceof Error ? exception.message : "删除失败", icon: "none" });
      }
    },
  });
}
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form diaper-page">
    <PageHeader :title="recordId ? '编辑尿便记录' : `记录${recordTypeLabel}`" back />
    <AppLoading v-if="loading" copy="正在加载尿便记录" />
    <view v-else class="diaper-form surface">
      <view class="field">
        <text class="field__label">类型</text>
        <SegmentedControl v-model="form.recordType" :options="recordTypeOptions" />
      </view>
      <view class="field">
        <text class="field__label">发生时间</text>
        <DateTimeField v-model="form.recordTime" title="选择发生时间" quick-record :max-now-offset-minutes="5" />
      </view>
      <view class="field">
        <text class="field__label">备注</text>
        <NInput v-model:value="form.note" type="textarea" :maxlength="500" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" />
      </view>
      <view class="form-actions">
        <NButton v-if="recordId" type="error" size="large" secondary block @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : recordId ? "保存修改" : `记录${recordTypeLabel}` }}</NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.diaper-form {
  padding: 16px;
}

</style>
