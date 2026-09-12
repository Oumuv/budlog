<script setup lang="ts">
import { Check, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-vue-next";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import EmptyState from "../../components/EmptyState.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { TaskStatus, TodoTask } from "../../types";
import { formatDateTime, todayKey } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

type Filter = "ALL" | TaskStatus;
interface TaskGroup { key: TaskStatus; title: string; tasks: TodoTask[] }

const filter = ref<Filter>("TODO");
const tasks = ref<TodoTask[]>([]);
const loading = ref(false);
const error = ref("");
const filterOptions: Array<{ value: Filter; label: string }> = [
  { value: "TODO", label: "待处理" },
  { value: "ALL", label: "全部" },
  { value: "DONE", label: "已完成" },
  { value: "CANCELED", label: "已取消" },
];

const todayHeading = computed(() => {
  const [, month, day] = todayKey().split("-");
  return `今天 · ${Number(month)}月${Number(day)}日`;
});
const groups = computed<TaskGroup[]>(() => {
  const candidates: TaskGroup[] = [
    { key: "TODO", title: todayHeading.value, tasks: tasks.value.filter((task) => task.status === "TODO") },
    { key: "DONE", title: "已完成", tasks: tasks.value.filter((task) => task.status === "DONE") },
    { key: "CANCELED", title: "已取消", tasks: tasks.value.filter((task) => task.status === "CANCELED") },
  ];
  return candidates.filter((group) => (
    filter.value === "ALL"
    || group.key === filter.value
    || (filter.value === "TODO" && group.key === "DONE")
  ) && group.tasks.length);
});
const visibleCount = computed(() => groups.value.reduce((sum, group) => sum + group.tasks.length, 0));

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
    confirmColor: "#e84d6f",
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
        <button class="add-task" @click="go('/pages/task-edit/index')"><Plus :size="18" />新增</button>
      </view>

      <view class="task-filters surface">
        <button v-for="option in filterOptions" :key="option.value" class="task-filter" :class="{ 'task-filter--active': filter === option.value }" @click="filter = option.value">{{ option.label }}</button>
      </view>

      <AppLoading v-if="loading" class="task-state" copy="正在整理任务" />
      <ErrorState v-else-if="error" class="task-state" title="任务加载失败" :copy="error" @retry="load" />
      <template v-else-if="visibleCount">
        <view v-for="group in groups" :key="group.key" class="task-group">
          <view class="task-group__head"><text>{{ group.title }}</text><small>{{ group.tasks.length }} 个{{ group.key === 'TODO' ? '待处理' : '' }}</small></view>
          <view class="task-list">
            <view v-for="task in group.tasks" :key="task.id" class="task-card surface" :class="`task-card--${task.status.toLowerCase()}`">
              <button v-if="task.status === 'TODO'" class="task-check" aria-label="标记完成" title="标记完成" @click="setStatus(task, 'DONE')" />
              <button v-else class="task-check task-check--done" aria-label="恢复为待处理" title="恢复为待处理" @click="setStatus(task, 'TODO')"><Check :size="15" /></button>
              <view class="task-card__body" @click="go(`/pages/task-edit/index?id=${task.id}`)">
                <view class="task-card__title-line">
                  <text class="task-card__title">{{ task.title }}</text>
                  <text class="task-card__status" :class="`task-card__status--${task.status.toLowerCase()}`">{{ statusLabel(task.status) }}</text>
                </view>
                <text class="task-card__time" :class="{ 'danger-text': task.overdue }">{{ formatDateTime(task.dueTime) }}</text>
                <text v-if="task.description" class="task-card__description">{{ task.description }}</text>
              </view>
              <view class="task-card__actions">
                <button class="task-action" aria-label="编辑任务" title="编辑任务" @click="go(`/pages/task-edit/index?id=${task.id}`)"><Pencil :size="15" /></button>
                <button v-if="task.status === 'TODO'" class="task-action" aria-label="取消任务" title="取消任务" @click="setStatus(task, 'CANCELED')"><X :size="16" /></button>
                <button v-if="task.status !== 'TODO'" class="task-action" aria-label="恢复任务" title="恢复任务" @click="setStatus(task, 'TODO')"><RotateCcw :size="15" /></button>
                <button class="task-action task-action--delete" aria-label="删除任务" title="删除任务" @click="remove(task)"><Trash2 :size="15" /></button>
              </view>
            </view>
          </view>
        </view>
      </template>
      <EmptyState v-else class="task-state" title="当前没有任务" copy="新增提醒后，会按到期时间集中显示在这里">
        <button class="add-task add-task--empty" @click="go('/pages/task-edit/index')"><Plus :size="16" />创建任务</button>
      </EmptyState>

      <AppNav current="tasks" />
    </view>
  </AppPage>
</template>

<style scoped>
.tasks-page { padding-top: max(22px, env(safe-area-inset-top)); }
.tasks-head { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; padding: 0 5px; }
.add-task { display: inline-flex; min-height: 40px; align-items: center; justify-content: center; gap: 5px; margin: 0; padding: 0 14px; border: 0; border-radius: 8px; color: #fff; background: var(--bud-color-primary); box-shadow: 0 7px 16px rgba(255, 79, 135, 0.22); font-size: 13px; font-weight: 750; }
.add-task--empty { margin-top: 4px; min-height: 36px; }
.task-filters { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 3px; padding: 4px; }
.task-filter { height: 34px; margin: 0; padding: 0 3px; border: 0; border-radius: 7px; color: var(--bud-color-muted); background: transparent; font-size: 10px; line-height: 34px; white-space: nowrap; }
.task-filter--active { color: var(--bud-color-primary); background: var(--bud-color-primary-soft); font-weight: 750; }
.task-state { margin-top: 16px; }
.task-group { margin-top: 18px; }
.task-group__head { display: flex; min-height: 28px; align-items: center; justify-content: space-between; margin-bottom: 7px; padding: 0 4px; font-size: 15px; font-weight: 800; }
.task-group__head small { color: var(--bud-color-muted); font-size: 10px; font-weight: 500; }
.task-list { display: grid; gap: 8px; }
.task-card { display: grid; min-width: 0; grid-template-columns: 26px minmax(0, 1fr) auto; align-items: start; gap: 8px; padding: 12px 9px 11px 11px; }
.task-card--done { opacity: 0.92; }
.task-card--canceled { opacity: 0.72; }
.task-check { display: flex; width: 22px; height: 22px; align-items: center; justify-content: center; margin: 1px 0 0; padding: 0; border: 1.5px solid #b9c4d5; border-radius: 50%; background: #fff; }
.task-check--done { border-color: #48c997; color: #1e9c70; background: #e2f8ef; }
.task-card__body { min-width: 0; }
.task-card__title-line { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 6px; }
.task-card__title { min-width: 0; overflow-wrap: anywhere; font-size: 13px; line-height: 19px; font-weight: 750; }
.task-card__status { flex: 0 0 auto; padding: 3px 6px; border-radius: 5px; font-size: 9px; font-weight: 700; }
.task-card__status--todo { color: #c27b09; background: var(--baby-yellow-soft); }
.task-card__status--done { color: #1b9b6c; background: var(--baby-green-soft); }
.task-card__status--canceled { color: var(--bud-color-muted); background: #f1f3f6; }
.task-card__time { display: block; margin-top: 3px; color: var(--bud-color-muted); font-size: 10px; }
.task-card__description { display: -webkit-box; overflow: hidden; margin-top: 4px; color: var(--bud-color-body); font-size: 10px; line-height: 15px; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.task-card__actions { display: flex; align-items: center; gap: 1px; padding-top: 22px; }
.task-action { display: flex; width: 27px; height: 27px; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 6px; color: #8d9ab0; background: transparent; }
.task-action--delete { color: var(--bud-color-primary); }
@media (max-width: 350px) {
  .task-card { grid-template-columns: 24px minmax(0, 1fr); }
  .task-card__actions { grid-column: 2; justify-content: flex-end; padding-top: 0; }
}
</style>
