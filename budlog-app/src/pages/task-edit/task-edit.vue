<script setup lang="ts">
import { Save, Trash2 } from "lucide-vue-next";
import { onBackPress, onLoad } from "@dcloudio/uni-app";
import { nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import DateTimeField from "../../components/DateTimeField.vue";
import PageHeader from "../../components/PageHeader.vue";
import { toIso, toLocalInput, uuid } from "../../utils/date";
import { switchValue } from "../../utils/events";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

const dueDefault = toLocalInput(new Date(Date.now() + 60 * 60 * 1000).toISOString());
const remindDefault = toLocalInput(new Date(Date.now() + 30 * 60 * 1000).toISOString());
const taskId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const dirty = ref(false);
let hydrating = true;

const form = reactive({
  clientRequestId: uuid(),
  title: "",
  description: "",
  dueTime: dueDefault,
  remindTime: remindDefault,
  hasReminder: true,
});

watch(form, () => {
  if (!hydrating) dirty.value = true;
}, { deep: true });

onLoad(async (options) => {
  if (!(await ensureAccess())) return;
  const id = Number(options?.id || 0);
  if (id) {
    taskId.value = id;
    loading.value = true;
    try {
      const task = await api.task(id);
      form.clientRequestId = task.clientRequestId;
      form.title = task.title;
      form.description = task.description || "";
      form.dueTime = toLocalInput(task.dueTime);
      form.remindTime = toLocalInput(task.remindTime || task.dueTime);
      form.hasReminder = Boolean(task.remindTime);
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
  if (!form.title.trim()) {
    uni.showToast({ title: "请输入任务标题", icon: "none" });
    return;
  }
  if (form.hasReminder && new Date(form.remindTime).getTime() > new Date(form.dueTime).getTime()) {
    uni.showToast({ title: "提醒时间不能晚于到期时间", icon: "none" });
    return;
  }
  saving.value = true;
  try {
    const payload = {
      clientRequestId: form.clientRequestId,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      dueTime: toIso(form.dueTime),
      remindTime: form.hasReminder ? toIso(form.remindTime) : undefined,
    };
    if (taskId.value) await api.updateTask(taskId.value, payload);
    else await api.createTask(payload);
    if (taskId.value) clearShownReminder(taskId.value);
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
  if (!taskId.value) return;
  uni.showModal({
    title: "删除任务",
    content: form.title,
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !taskId.value) return;
      try {
        await api.deleteTask(taskId.value);
        clearShownReminder(taskId.value);
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
  <view class="page-shell page-shell--form task-edit-page">
    <PageHeader :title="taskId ? '编辑任务' : '新增任务'" back />
    <view v-if="loading" class="surface empty-state">加载中</view>
    <view v-else class="task-form surface">
      <view class="field">
        <text class="field__label">标题</text>
        <input v-model="form.title" class="field__control" maxlength="100" placeholder="任务标题" />
      </view>
      <view class="field">
        <text class="field__label">说明</text>
        <textarea v-model="form.description" class="field__control" maxlength="1000" placeholder="可选" />
      </view>
      <view class="field">
        <text class="field__label">到期时间</text>
        <DateTimeField v-model="form.dueTime" />
      </view>
      <view class="reminder-toggle">
        <text>到期提醒</text>
        <switch :checked="form.hasReminder" color="#216454" @change="form.hasReminder = switchValue($event)" />
      </view>
      <view v-if="form.hasReminder" class="field">
        <text class="field__label">提醒时间</text>
        <DateTimeField v-model="form.remindTime" />
      </view>
      <view class="form-actions">
        <button v-if="taskId" class="btn btn--danger" @click="remove"><Trash2 :size="18" />删除</button>
        <button class="btn btn--primary" :disabled="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : "保存" }}</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.task-form {
  padding: 16px;
}

.reminder-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  margin-bottom: 14px;
  border-top: 1px solid #edf0ee;
}
</style>
