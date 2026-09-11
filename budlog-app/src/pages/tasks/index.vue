<script setup lang="ts">
import { CalendarDays, Check, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-vue-next";
import { NButton } from "naive-ui";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import EmptyState from "../../components/EmptyState.vue";
import ErrorState from "../../components/ErrorState.vue";
import SegmentedControl from "../../components/SegmentedControl.vue";
import type { TaskStatus, TodoTask } from "../../types";
import { formatDateTime } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

type Filter = "ALL" | TaskStatus;

const filter = ref<Filter>("TODO");
const tasks = ref<TodoTask[]>([]);
const loading = ref(false);
const error = ref("");
const filterOptions = [
  { value: "TODO", label: "待处理" },
  { value: "ALL", label: "全部" },
  { value: "DONE", label: "已完成" },
  { value: "CANCELED", label: "已取消" },
];

const filtered = computed(() =>
  filter.value === "ALL" ? tasks.value : tasks.value.filter((task) => task.status === filter.value),
);

onShow(async () => {
  if (await ensureAccess()) await load();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    tasks.value = await api.tasks();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "任务加载失败";
  } finally {
    loading.value = false;
  }
}

async function setStatus(task: TodoTask, status: TaskStatus) {
  try {
    await api.updateTaskStatus(task.id, status);
    clearShownReminder(task.id);
    await load();
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "任务更新失败", icon: "none" });
  }
}

function go(url: string) {
  uni.navigateTo({ url });
}

function remove(task: TodoTask) {
  uni.showModal({
    title: "删除任务",
    content: task.title,
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm) return;
      try {
        await api.deleteTask(task.id);
        clearShownReminder(task.id);
        await load();
      } catch (exception) {
        uni.showToast({ title: exception instanceof Error ? exception.message : "删除失败", icon: "none" });
      }
    },
  });
}

function statusLabel(status: TaskStatus) {
  return status === "TODO" ? "待处理" : status === "DONE" ? "已完成" : "已取消";
}
</script>

<template>
  <AppPage>
    <view class="page-shell tasks-page">
    <view class="tasks-head">
      <view>
        <text class="page-title">任务</text>
        <text class="page-subtitle">把照护事项放在同一个地方</text>
      </view>
      <view class="tasks-head__actions">
        <button class="icon-btn" aria-label="育儿日历" title="查看日历" @click="go('/pages/calendar/index')"><CalendarDays :size="21" /></button>
        <NButton type="primary" size="medium" @click="go('/pages/task-edit/index')">
          <Plus :size="17" />新增
        </NButton>
      </view>
    </view>

    <SegmentedControl v-model="filter" :options="filterOptions" />

    <AppLoading v-if="loading" class="task-state" copy="正在整理任务" />
    <ErrorState v-else-if="error" class="task-state" title="任务加载失败" :copy="error" @retry="load" />
    <view v-else-if="filtered.length" class="task-list">
      <view v-for="task in filtered" :key="task.id" class="task-card surface">
        <view class="task-card__top">
          <view class="task-card__body" @click="go(`/pages/task-edit/index?id=${task.id}`)">
            <view class="task-card__title-row">
              <text class="task-card__title">{{ task.title }}</text>
              <text class="task-card__status" :class="`task-card__status--${task.status.toLowerCase()}`">{{ statusLabel(task.status) }}</text>
            </view>
            <text :class="task.overdue ? 'danger-text' : 'muted'">{{ formatDateTime(task.dueTime) }}</text>
            <text v-if="task.description" class="task-card__description">{{ task.description }}</text>
          </view>
        </view>
        <view class="task-card__actions">
          <button v-if="task.status === 'TODO'" class="icon-btn task-card__complete" aria-label="完成" title="标记完成" @click="setStatus(task, 'DONE')"><Check :size="19" /></button>
          <button v-else class="icon-btn task-card__restore" aria-label="恢复" title="恢复为待处理" @click="setStatus(task, 'TODO')"><RotateCcw :size="18" /></button>
          <button v-if="task.status === 'TODO'" class="icon-btn task-card__cancel" aria-label="取消" title="取消任务" @click="setStatus(task, 'CANCELED')"><X :size="19" /></button>
          <button class="icon-btn task-card__edit" aria-label="编辑" title="编辑任务" @click="go(`/pages/task-edit/index?id=${task.id}`)"><Pencil :size="18" /></button>
          <button class="icon-btn task-card__delete" aria-label="删除" title="删除任务" @click="remove(task)"><Trash2 :size="18" /></button>
        </view>
      </view>
    </view>
    <EmptyState v-else class="task-state" title="当前没有任务" copy="新增提醒后，会按到期时间集中显示在这里">
      <NButton type="primary" size="medium" @click="go('/pages/task-edit/index')">创建任务</NButton>
    </EmptyState>

    <AppNav current="tasks" />
    </view>
  </AppPage>
</template>

<style scoped>
.tasks-head__actions { display: flex; flex: none; align-items: center; gap: 6px; }
.tasks-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 20px;
}

.task-state {
  margin-top: 18px;
}

.task-error {
  color: #a33e43;
  background: var(--bud-color-coral-soft);
}

.task-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.task-card {
  overflow: hidden;
}

.task-card__top {
  padding: 15px;
}

.task-card__body,
.task-card__title {
  min-width: 0;
}

.task-card__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
}

.task-card__title {
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 720;
}

.task-card__status {
  flex: none;
  padding: 3px 6px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
}

.task-card__status--todo { color: var(--bud-color-gold); background: var(--bud-color-gold-soft); }
.task-card__status--done { color: var(--bud-color-sage); background: var(--bud-color-sage-soft); }
.task-card__status--canceled { color: var(--bud-color-muted); background: #f3eeee; }

.task-card__body > text {
  display: block;
  font-size: 12px;
}

.task-card__description {
  display: -webkit-box !important;
  overflow: hidden;
  margin-top: 8px;
  color: var(--bud-color-body);
  font-size: 13px !important;
  line-height: 19px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.task-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  padding: 6px 8px;
  border-top: 1px solid var(--bud-color-line-soft);
  background: #fffbfb;
}

.task-card__actions .icon-btn { width: 36px; height: 36px; }
.task-card__complete,
.task-card__restore { color: var(--bud-color-sage); background: var(--bud-color-sage-soft); }
.task-card__cancel { color: var(--bud-color-gold); background: var(--bud-color-gold-soft); }
.task-card__edit { color: var(--bud-color-primary); background: var(--bud-color-primary-soft); }
.task-card__delete { color: #a33e43; background: var(--bud-color-coral-soft); }
</style>
