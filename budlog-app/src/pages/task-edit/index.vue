<script setup lang="ts">
import { Save, Trash2 } from "lucide-vue-next";
import { NButton, NInput, NSwitch } from "naive-ui";
import { onBackPress, onLoad } from "@dcloudio/uni-app";
import { nextTick, reactive, ref, watch } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppPage from "../../components/AppPage.vue";
import DateTimeField from "../../components/DateTimeField.vue";
import PageHeader from "../../components/PageHeader.vue";
import SegmentedControl from "../../components/SegmentedControl.vue";
import { shiftDay, toIso, toLocalInput, todayKey, uuid } from "../../utils/date";
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
type DuePreset = "30M" | "1H" | "TONIGHT" | "TOMORROW" | "";
type ReminderLead = "10" | "30" | "60" | "CUSTOM";
const duePreset = ref<DuePreset>("");
const reminderLead = ref<ReminderLead>("30");
const duePresetOptions: Array<{ value: Exclude<DuePreset, "">; label: string }> = [
  { value: "30M", label: "30 分钟后" },
  { value: "1H", label: "1 小时后" },
  { value: "TONIGHT", label: "今晚" },
  { value: "TOMORROW", label: "明早" },
];
const reminderOptions = [
  { value: "10", label: "10分" },
  { value: "30", label: "30分" },
  { value: "60", label: "1小时" },
  { value: "CUSTOM", label: "自定义" },
];

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
      reminderLead.value = inferReminderLead();
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
  if (form.hasReminder && new Date(toIso(form.remindTime)).getTime() > new Date(toIso(form.dueTime)).getTime()) {
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

function setDuePreset(preset: Exclude<DuePreset, "">) {
  duePreset.value = preset;
  if (preset === "30M" || preset === "1H") {
    const minutes = preset === "30M" ? 30 : 60;
    form.dueTime = toLocalInput(new Date(Date.now() + minutes * 60_000).toISOString());
  } else {
    const today = todayKey();
    const tonight = `${today}T20:00`;
    if (preset === "TONIGHT") {
      form.dueTime = new Date(toIso(tonight)).getTime() > Date.now() ? tonight : `${shiftDay(today, 1)}T20:00`;
    } else {
      form.dueTime = `${shiftDay(today, 1)}T09:00`;
    }
  }
  syncReminderFromDue();
}

function updateDueTime(value: string) {
  duePreset.value = "";
  form.dueTime = value;
}

function syncReminderFromDue() {
  if (!form.hasReminder || reminderLead.value === "CUSTOM") return;
  const leadMinutes = Number(reminderLead.value);
  const reminderInstant = new Date(toIso(form.dueTime)).getTime() - leadMinutes * 60_000;
  form.remindTime = toLocalInput(new Date(reminderInstant).toISOString());
}

function inferReminderLead(): ReminderLead {
  if (!form.hasReminder) return "30";
  try {
    const dueInstant = new Date(toIso(form.dueTime)).getTime();
    const remindInstant = new Date(toIso(form.remindTime)).getTime();
    const difference = Math.round((dueInstant - remindInstant) / 60_000);
    return [10, 30, 60].includes(difference) ? String(difference) as ReminderLead : "CUSTOM";
  } catch {
    return "CUSTOM";
  }
}

watch(() => form.dueTime, syncReminderFromDue);
watch(reminderLead, syncReminderFromDue);
watch(() => form.hasReminder, (enabled) => {
  if (enabled) syncReminderFromDue();
});
</script>

<template>
  <AppPage>
    <view class="page-shell page-shell--form task-edit-page">
    <PageHeader :title="taskId ? '编辑任务' : '新增任务'" back />
    <AppLoading v-if="loading" copy="正在加载任务" />
    <view v-else class="task-form surface">
      <view class="field">
        <text class="field__label">标题</text>
        <NInput v-model:value="form.title" clearable :maxlength="100" placeholder="任务标题" />
      </view>
      <view class="field">
        <text class="field__label">说明</text>
        <NInput v-model:value="form.description" type="textarea" :maxlength="1000" :autosize="{ minRows: 3, maxRows: 7 }" placeholder="可选" />
      </view>
      <view class="field">
        <text class="field__label">到期时间</text>
        <DateTimeField :model-value="form.dueTime" title="选择到期时间" @update:model-value="updateDueTime" />
        <view class="shortcut-row due-shortcuts">
          <button
            v-for="option in duePresetOptions"
            :key="option.value"
            class="shortcut-chip"
            :class="{ 'shortcut-chip--active': duePreset === option.value }"
            @click="setDuePreset(option.value)"
          >
            {{ option.label }}
          </button>
        </view>
      </view>
      <view class="reminder-toggle">
        <view>
          <text class="reminder-toggle__title">到期提醒</text>
          <text class="reminder-toggle__copy">在到期前提醒家庭成员</text>
        </view>
        <NSwitch v-model:value="form.hasReminder" />
      </view>
      <view v-if="form.hasReminder" class="field">
        <text class="field__label">提前提醒</text>
        <SegmentedControl v-model="reminderLead" class="reminder-segmented" :options="reminderOptions" />
        <DateTimeField v-if="reminderLead === 'CUSTOM'" v-model="form.remindTime" title="选择提醒时间" />
      </view>
      <view class="form-actions">
        <NButton v-if="taskId" type="error" size="large" secondary block @click="remove"><Trash2 :size="18" />删除</NButton>
        <NButton type="primary" size="large" block :loading="saving" @click="save"><Save :size="18" />{{ saving ? "保存中" : "保存" }}</NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.task-form {
  padding: 16px;
}

.reminder-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  gap: 16px;
  margin-bottom: 14px;
  border-top: 1px solid var(--bud-color-line-soft);
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.reminder-toggle__title,
.reminder-toggle__copy {
  display: block;
}

.reminder-toggle__title {
  font-size: 14px;
  font-weight: 650;
}

.reminder-toggle__copy {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.due-shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.due-shortcuts .shortcut-chip {
  width: 100%;
  padding-right: 5px;
  padding-left: 5px;
}

.reminder-segmented {
  margin-bottom: 10px;
}

@media (max-width: 380px) {
  .due-shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
