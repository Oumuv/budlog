<script setup lang="ts">
import { FileText, Pencil } from "lucide-vue-next";
import { onLoad, onShow, onUnload } from "@dcloudio/uni-app";
import { ref } from "vue";
import { api } from "../../api";
import PageHeader from "../../components/PageHeader.vue";
import type { CalendarCategory } from "../../types";
import { categoryLabels, eventLabels, taskLabels } from "../../utils/calendar";
import { formatDuration, setAppTimezone, toLocalInput } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

interface Detail {
  title: string;
  kind: string;
  fields: Array<{ label: string; value: string }>;
  note?: string;
  editUrl?: string;
}

const loading = ref(true);
const error = ref("");
const detail = ref<Detail>();
let category: CalendarCategory;
let id = 0;
let code = "";
let valid = false;
let requestId = 0;

onLoad((options) => {
  category = String(options?.category || "") as CalendarCategory;
  id = Number(options?.id || 0);
  code = String(options?.code || "");
  const validId = Number.isSafeInteger(id) && id > 0;
  const systemCode = ["FULL_MONTH", "DAY_100", "HALF_YEAR", "ONE_YEAR"].includes(code);
  valid = Object.prototype.hasOwnProperty.call(categoryLabels, category)
    && (category === "MILESTONE" ? validId || systemCode : validId);
});

onShow(async () => {
  if (await ensureAccess()) await load();
});
onUnload(() => { requestId += 1; });

const dateTime = (value?: string) => value ? toLocalInput(value).replace("T", " ") : "未设置";
const field = (label: string, value: string) => ({ label, value });

async function load() {
  const current = ++requestId;
  loading.value = true;
  error.value = "";
  detail.value = undefined;
  try {
    if (!valid) throw new Error("事项链接无效，请返回日历重新选择");
    const baby = await api.getBaby();
    if (current !== requestId) return;
    if (!baby) throw new Error("请先在设置中添加宝宝资料");
    setAppTimezone(baby.timezone);
    const result = await fetchDetail();
    if (current === requestId) detail.value = result;
  } catch (exception) {
    if (current === requestId) error.value = exception instanceof Error ? exception.message : "详情加载失败";
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function fetchDetail(): Promise<Detail> {
  const kind = categoryLabels[category];
  switch (category) {
    case "TASK": {
      const task = await api.task(id);
      return {
        title: task.title, kind, note: task.description,
        editUrl: `/pages/task-edit/index?id=${id}`,
        fields: [
          field("状态", task.overdue ? "待处理 · 已逾期" : taskLabels[task.status]),
          field("到期时间", dateTime(task.dueTime)),
          field("提醒时间", dateTime(task.remindTime)),
          ...(task.completedAt ? [field("完成时间", dateTime(task.completedAt))] : []),
        ],
      };
    }
    case "MILESTONE": {
      const milestones = await api.milestones();
      const milestone = milestones.find((item) => id > 0 ? item.id === id : item.system && item.code === code);
      if (!milestone) throw new Error("关键节点不存在或已删除，请返回日历刷新");
      const days = milestone.daysDifference;
      const rules: Record<string, string> = {
        FULL_MONTH: "出生时间加 1 个自然月",
        DAY_100: "出生日计为第 1 天，出生时间加 99 天",
        HALF_YEAR: "出生时间加 6 个自然月",
        ONE_YEAR: "出生时间加 1 个自然年",
      };
      return {
        title: milestone.title, kind, note: milestone.note,
        fields: [
          field("节点类型", milestone.system ? "系统成长节点" : "自定义纪念日"),
          field("节点时间", dateTime(milestone.targetTime)),
          field("距离今天", days === 0 ? "就是今天" : days > 0 ? `还有 ${days} 天` : `已过去 ${Math.abs(days)} 天`),
          ...(milestone.system ? [field("计算方式", rules[milestone.code] || "根据宝宝出生时间计算")] : []),
        ],
      };
    }
    case "EVENT": {
      const record = await api.event(id);
      return {
        title: record.title, kind, note: record.note, editUrl: `/pages/event/index?id=${id}`,
        fields: [field("事件分类", eventLabels[record.eventType]), field("发生时间", dateTime(record.occurredAt))],
      };
    }
    case "FEEDING": {
      const record = await api.feeding(id);
      const types = { BREAST_DIRECT: "母乳亲喂", BREAST_BOTTLE: "母乳瓶喂", FORMULA_BOTTLE: "奶粉瓶喂" };
      const sides = { LEFT: "左侧", RIGHT: "右侧", BOTH: "双侧" };
      return {
        title: types[record.feedingType], kind, note: record.note, editUrl: `/pages/feeding/index?id=${id}`,
        fields: [
          field("喂养方式", types[record.feedingType]),
          field("开始时间", dateTime(record.startTime)),
          ...(record.endTime ? [field("结束时间", dateTime(record.endTime))] : []),
          ...(record.breastSide ? [field("亲喂侧别", sides[record.breastSide])] : []),
          ...(record.amountMl != null ? [field("奶量", `${record.amountMl} ml`)] : []),
          ...(record.durationMinutes != null ? [field("喂养时长", formatDuration(record.durationMinutes))] : []),
          ...(record.minutesSincePrevious != null ? [field("距上次喂奶", formatDuration(record.minutesSincePrevious))] : []),
        ],
      };
    }
    case "DIAPER": {
      const record = await api.diaper(id);
      const title = { PEE: "尿尿", POOP: "便便", BOTH: "尿便都有" }[record.recordType];
      return { title, kind, note: record.note, editUrl: `/pages/diaper/index?id=${id}`,
        fields: [field("记录类型", title), field("记录时间", dateTime(record.recordTime))] };
    }
    case "MILK_STORAGE": {
      const record = await api.milkStorage(id);
      return { title: "存奶记录", kind, note: record.note, editUrl: `/pages/milk-storage/index?id=${id}`,
        fields: [field("存奶时间", dateTime(record.storedAt)), field("存奶量", `${record.amountMl} ml`)] };
    }
    case "WEIGHT": {
      const record = await api.weightRecord(id);
      return { title: "体重记录", kind, note: record.note, editUrl: `/pages/weight/index?id=${id}`,
        fields: [field("测量时间", dateTime(record.measuredAt)), field("体重", `${record.weightKg} kg`)] };
    }
    default: throw new Error("暂不支持此类事项");
  }
}

function edit() {
  if (detail.value?.editUrl) uni.navigateTo({ url: detail.value.editUrl });
}
</script>

<template>
  <view class="page-shell page-shell--form detail-page">
    <PageHeader title="事项详情" back />
    <view v-if="loading" class="state-panel surface">
      <wd-loading color="#b94b5d" />
      <text class="state-panel__copy">正在加载事项详情</text>
    </view>
    <view v-else-if="error" class="state-panel surface">
      <text class="state-panel__title">暂时无法查看</text>
      <text class="state-panel__copy">{{ error }}</text>
      <wd-button type="info" @click="load">重试</wd-button>
    </view>
    <template v-else-if="detail">
      <view class="detail-card surface">
        <text class="detail-kind">{{ detail.kind }}</text>
        <text class="detail-title">{{ detail.title }}</text>
        <view class="detail-fields">
          <view v-for="row in detail.fields" :key="row.label" class="detail-field">
            <text class="detail-label">{{ row.label }}</text><text class="detail-value">{{ row.value }}</text>
          </view>
        </view>
      </view>
      <view class="detail-notes surface">
        <view class="detail-notes-heading"><FileText :size="17" /><text>{{ category === 'TASK' ? '任务说明' : '备注' }}</text></view>
        <text class="detail-note" :class="{ muted: !detail.note }">{{ detail.note || '暂无补充说明' }}</text>
      </view>
      <wd-button v-if="detail.editUrl" :round="false" size="large" block @click="edit"><Pencil :size="17" />编辑此事项</wd-button>
    </template>
  </view>
</template>

<style scoped>
.detail-card { padding: 20px 18px 6px; }
.detail-kind { display: inline-block; padding: 4px 8px; border-radius: 5px; color: var(--bud-color-primary-dark); background: var(--bud-color-primary-soft); font-size: 12px; }
.detail-title { display: block; margin: 12px 0 20px; font-size: 23px; font-weight: 730; line-height: 1.5; overflow-wrap: anywhere; }
.detail-field { display: flex; align-items: flex-start; gap: 14px; padding: 14px 0; border-top: 1px solid var(--bud-color-line-soft); font-size: 14px; line-height: 1.6; }
.detail-label { width: 76px; flex: none; color: var(--bud-color-muted); }
.detail-value { min-width: 0; flex: 1; text-align: right; overflow-wrap: anywhere; }
.detail-notes { margin: 16px 0 24px; padding: 18px; }
.detail-notes-heading { display: flex; align-items: center; gap: 7px; margin-bottom: 12px; color: var(--bud-color-body); font-size: 14px; font-weight: 650; }
.detail-note { display: block; font-size: 14px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
