<script setup lang="ts">
import { Check, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-vue-next";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppNav from "../../components/AppNav.vue";
import type { TaskStatus, TodoTask } from "../../types";
import { formatDateTime } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";
import { clearShownReminder } from "../../utils/reminders";

type Filter = "ALL" | TaskStatus;

const filter = ref<Filter>("TODO");
const tasks = ref<TodoTask[]>([]);
const loading = ref(false);
const error = ref("");

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
  <view class="page-shell tasks-page">
    <view class="tasks-head">
      <text class="page-title">任务</text>
      <button class="btn btn--primary tasks-add" @click="go('/pages/task-edit/task-edit')"><Plus :size="18" />新增</button>
    </view>

    <view class="segmented task-filters">
      <view
        v-for="item in [
          { key: 'TODO', label: '待处理' },
          { key: 'ALL', label: '全部' },
          { key: 'DONE', label: '已完成' },
          { key: 'CANCELED', label: '已取消' },
        ]"
        :key="item.key"
        class="segmented__item"
        :class="{ 'segmented__item--active': filter === item.key }"
        @click="filter = item.key as Filter"
      >{{ item.label }}</view>
    </view>

    <view v-if="loading" class="surface empty-state task-state">加载中</view>
    <view v-else-if="error" class="surface empty-state task-state">
      <text class="danger-text">{{ error }}</text>
      <button class="btn btn--secondary task-retry" @click="load">重试</button>
    </view>
    <view v-else-if="filtered.length" class="task-list">
      <view v-for="task in filtered" :key="task.id" class="task-card surface">
        <view class="task-card__top">
          <view class="task-card__body" @click="go(`/pages/task-edit/task-edit?id=${task.id}`)">
            <view class="task-card__title-row">
              <text class="task-card__title">{{ task.title }}</text>
              <text class="task-card__status" :class="`task-card__status--${task.status.toLowerCase()}`">{{ statusLabel(task.status) }}</text>
            </view>
            <text :class="task.overdue ? 'danger-text' : 'muted'">{{ formatDateTime(task.dueTime) }}</text>
            <text v-if="task.description" class="task-card__description">{{ task.description }}</text>
          </view>
        </view>
        <view class="task-card__actions">
          <button v-if="task.status === 'TODO'" class="icon-btn" aria-label="完成" @click="setStatus(task, 'DONE')"><Check :size="19" /></button>
          <button v-else class="icon-btn" aria-label="恢复" @click="setStatus(task, 'TODO')"><RotateCcw :size="18" /></button>
          <button v-if="task.status === 'TODO'" class="icon-btn" aria-label="取消" @click="setStatus(task, 'CANCELED')"><X :size="19" /></button>
          <button class="icon-btn" aria-label="编辑" @click="go(`/pages/task-edit/task-edit?id=${task.id}`)"><Pencil :size="18" /></button>
          <button class="icon-btn task-card__delete" aria-label="删除" @click="remove(task)"><Trash2 :size="18" /></button>
        </view>
      </view>
    </view>
    <view v-else class="surface empty-state task-state">当前没有任务</view>

    <AppNav current="tasks" />
  </view>
</template>

<style scoped>
.tasks-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 18px;
}

.tasks-add {
  min-height: 40px;
  padding: 0 14px;
}

.task-filters {
  overflow-x: auto;
}

.task-state {
  margin-top: 18px;
}

.task-retry {
  margin: 12px auto 0;
}

.task-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.task-card {
  overflow: hidden;
}

.task-card__top {
  padding: 14px;
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
  margin-bottom: 5px;
}

.task-card__title {
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 700;
}

.task-card__status {
  flex: none;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}

.task-card__status--todo { color: #6f5812; background: #f6edc7; }
.task-card__status--done { color: #216454; background: #e3f0eb; }
.task-card__status--canceled { color: #6e7773; background: #ecefed; }

.task-card__body > text {
  display: block;
  font-size: 12px;
}

.task-card__description {
  display: -webkit-box !important;
  overflow: hidden;
  margin-top: 8px;
  color: #53615c;
  font-size: 13px !important;
  line-height: 19px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.task-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  padding: 4px 8px;
  border-top: 1px solid #edf0ee;
  background: #fafbfa;
}

.task-card__delete { color: #a43835; }
</style>
