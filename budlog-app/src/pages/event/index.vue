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
import type { EventType } from "../../types";
import { nowLocalInput, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const recordId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dirty = ref(false);
let hydrating = true;

const form = reactive({
  clientRequestId: uuid(),
  eventType: "MOMENT" as EventType,
  title: "",
  occurredAt: nowLocalInput(),
  note: "",
});
const eventTypeOptions = [
  { value: "VACCINE", label: "疫苗" },
  { value: "DOCUMENT", label: "证件" },
  { value: "MOMENT", label: "小事" },
  { value: "OTHER", label: "其他" },
];
const eventTypeLabel = computed(() => eventTypeOptions.find((option) => option.value === form.eventType)?.label || "事件");

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const requestedDate = String(options?.date || "");
  if (/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) {
    form.occurredAt = `${requestedDate}T${nowLocalInput().slice(11)}`;
  }
  const requestedType = String(options?.type || "");
  if (eventTypeOptions.some((option) => option.value === requestedType)) {
    form.eventType = requestedType as EventType;
  }
  const id = Number(options?.id || 0);
  if (id) await loadRecord(id);
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

async function loadRecord(id: number) {
  loading.value = true;
  try {
    const record = await api.event(id);
    recordId.value = record.id;
    form.clientRequestId = record.clientRequestId;
    form.eventType = record.eventType;
    form.title = record.title;
    form.occurredAt = toLocalInput(record.occurredAt);
    form.note = record.note || "";
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

function validate(): string | undefined {
  if (!form.title.trim()) return "请输入事件标题";
  if (form.title.trim().length > 100) return "事件标题不能超过 100 个字符";
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
      eventType: form.eventType,
      title: form.title.trim(),
      occurredAt: toIso(form.occurredAt),
      note: form.note.trim() || undefined,
    };
    if (recordId.value) await api.updateEvent(recordId.value, payload);
    else await api.createEvent(payload);
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
    title: "删除事件记录",
    content: "删除后事件会从时间线中移除",
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !recordId.value) return;
      deleting.value = true;
      try {
        await api.deleteEvent(recordId.value);
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
  <AppPage>
    <view class="page-shell page-shell--form event-page">
    <PageHeader :title="recordId ? '编辑事件记录' : `记录${eventTypeLabel}`" back />

    <AppLoading v-if="loading" copy="正在加载事件记录" />

    <view v-else class="event-form surface">
      <view class="field">
        <text class="field__label">分类</text>
        <SegmentedControl v-model="form.eventType" :options="eventTypeOptions" />
      </view>

      <view class="field">
        <text class="field__label">标题</text>
        <NInput v-model:value="form.title" clearable :maxlength="100" placeholder="例如：接种乙肝疫苗第 2 针" />
      </view>

      <view class="field">
        <text class="field__label">发生时间</text>
        <DateTimeField v-model="form.occurredAt" title="选择发生时间" quick-record :max-now-offset-minutes="5" />
      </view>

      <view class="field">
        <text class="field__label">备注</text>
        <NInput v-model:value="form.note" type="textarea" :maxlength="1000" :autosize="{ minRows: 3, maxRows: 7 }" placeholder="可选，可记录地点、针次或其他细节" />
      </view>

      <view class="form-actions">
        <NButton v-if="recordId" type="error" size="large" secondary block :loading="deleting" @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : recordId ? "保存修改" : "记录事件" }}</NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.event-form {
  padding: 16px;
}

</style>
