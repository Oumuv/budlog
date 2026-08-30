<script setup lang="ts">
import { CalendarDays, LogOut, Pencil, Plus, Save, Trash2, X } from "lucide-vue-next";
import { onShow } from "@dcloudio/uni-app";
import { reactive, ref } from "vue";
import { api } from "../../api";
import AppNav from "../../components/AppNav.vue";
import DateTimeField from "../../components/DateTimeField.vue";
import type { AppSetting, Milestone } from "../../types";
import { clearPassword } from "../../utils/auth";
import { formatDateTime, nowLocalInput, setAppTimezone, toIso, toLocalInput, uuid } from "../../utils/date";
import { switchValue } from "../../utils/events";
import { ensureAccess, resetAccessVerification } from "../../utils/guard";

const timezones = ["Asia/Shanghai", "Asia/Hong_Kong", "Asia/Taipei", "Asia/Tokyo", "UTC"];
const loading = ref(false);
const savingProfile = ref(false);
const savingSettings = ref(false);
const error = ref("");
const configured = ref(false);
const milestones = ref<Milestone[]>([]);
const profile = reactive({ name: "", birthTime: nowLocalInput(), timezone: "Asia/Shanghai", note: "" });
const setting = reactive<AppSetting>({
  defaultFeedingIntervalMin: 180,
  feedingIntervalAnchor: "START",
  reminderSoundEnabled: false,
  reminderVibrateEnabled: true,
});
const milestoneModal = ref(false);
const milestoneId = ref<number>();
const milestoneForm = reactive({ clientRequestId: uuid(), title: "", targetTime: nowLocalInput(), note: "" });

onShow(async () => {
  if (await ensureAccess()) await load();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const dashboard = await api.dashboard();
    configured.value = dashboard.configured;
    Object.assign(setting, dashboard.settings);
    if (dashboard.baby) {
      setAppTimezone(dashboard.baby.timezone);
      profile.name = dashboard.baby.name;
      profile.birthTime = toLocalInput(dashboard.baby.birthTime, dashboard.baby.timezone);
      profile.timezone = dashboard.baby.timezone;
      profile.note = dashboard.baby.note || "";
      milestones.value = await api.milestones();
    } else {
      milestones.value = [];
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "设置加载失败";
  } finally {
    loading.value = false;
  }
}

async function saveProfile() {
  if (!profile.name.trim()) {
    uni.showToast({ title: "请输入宝宝昵称", icon: "none" });
    return;
  }
  savingProfile.value = true;
  try {
    await api.saveBaby({
      name: profile.name.trim(),
      birthTime: toIso(profile.birthTime, profile.timezone),
      timezone: profile.timezone,
      note: profile.note.trim() || undefined,
    });
    setAppTimezone(profile.timezone);
    configured.value = true;
    milestones.value = await api.milestones();
    uni.showToast({ title: "已保存", icon: "success" });
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  } finally {
    savingProfile.value = false;
  }
}

async function saveSetting() {
  savingSettings.value = true;
  try {
    Object.assign(
      setting,
      await api.saveSettings({
        defaultFeedingIntervalMin: Number(setting.defaultFeedingIntervalMin),
        reminderSoundEnabled: setting.reminderSoundEnabled,
        reminderVibrateEnabled: setting.reminderVibrateEnabled,
      }),
    );
    uni.showToast({ title: "已保存", icon: "success" });
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  } finally {
    savingSettings.value = false;
  }
}

function openMilestone(item?: Milestone) {
  milestoneId.value = item?.id;
  milestoneForm.clientRequestId = uuid();
  milestoneForm.title = item?.title || "";
  milestoneForm.targetTime = item
    ? toLocalInput(item.targetTime, profile.timezone)
    : nowLocalInput(profile.timezone);
  milestoneForm.note = item?.note || "";
  milestoneModal.value = true;
}

async function saveMilestone() {
  if (!milestoneForm.title.trim()) {
    uni.showToast({ title: "请输入纪念日名称", icon: "none" });
    return;
  }
  const payload = {
    clientRequestId: milestoneForm.clientRequestId,
    title: milestoneForm.title.trim(),
    targetTime: toIso(milestoneForm.targetTime, profile.timezone),
    note: milestoneForm.note.trim() || undefined,
  };
  try {
    if (milestoneId.value) await api.updateMilestone(milestoneId.value, payload);
    else await api.createMilestone(payload);
    milestoneModal.value = false;
    milestones.value = await api.milestones();
  } catch (exception) {
    uni.showToast({ title: exception instanceof Error ? exception.message : "保存失败", icon: "none" });
  }
}

function removeMilestone(item: Milestone) {
  if (!item.id) return;
  uni.showModal({
    title: "删除纪念日",
    content: item.title,
    confirmColor: "#a43835",
    success: async (result) => {
      if (!result.confirm || !item.id) return;
      try {
        await api.deleteMilestone(item.id);
        milestones.value = await api.milestones();
      } catch (exception) {
        uni.showToast({ title: exception instanceof Error ? exception.message : "删除失败", icon: "none" });
      }
    },
  });
}

function pickTimezone(event: { detail: { value: number } }) {
  const nextTimezone = timezones[Number(event.detail.value)];
  const instant = toIso(profile.birthTime, profile.timezone);
  profile.birthTime = toLocalInput(instant, nextTimezone);
  profile.timezone = nextTimezone;
}

function logout() {
  clearPassword();
  resetAccessVerification();
  uni.reLaunch({ url: "/pages/access/access" });
}

function milestoneStatus(days: number) {
  if (days === 0) return "今天";
  return days > 0 ? `${days} 天后` : `${Math.abs(days)} 天前`;
}
</script>

<template>
  <view class="page-shell settings-page">
    <view class="settings-head">
      <text class="page-title">设置</text>
      <button class="icon-btn settings-logout" aria-label="退出访问" @click="logout"><LogOut :size="20" /></button>
    </view>

    <view v-if="loading" class="surface empty-state">加载中</view>
    <view v-else-if="error" class="surface empty-state">
      <text class="danger-text">{{ error }}</text>
      <button class="btn btn--secondary settings-retry" @click="load">重试</button>
    </view>

    <template v-else>
      <view class="section settings-section">
        <view class="section-title"><text class="section-title__text">宝宝资料</text></view>
        <view class="settings-form surface">
          <view class="field">
            <text class="field__label">昵称</text>
            <input v-model="profile.name" class="field__control" maxlength="50" placeholder="宝宝昵称" />
          </view>
          <view class="field">
            <text class="field__label">出生时间</text>
            <DateTimeField v-model="profile.birthTime" />
          </view>
          <view class="field">
            <text class="field__label">时区</text>
            <picker :range="timezones" :value="Math.max(0, timezones.indexOf(profile.timezone))" @change="pickTimezone">
              <view class="field__control picker-control">{{ profile.timezone }}</view>
            </picker>
          </view>
          <view class="field">
            <text class="field__label">备注</text>
            <textarea v-model="profile.note" class="field__control" maxlength="500" placeholder="可选" />
          </view>
          <button class="btn btn--primary settings-save" :disabled="savingProfile" @click="saveProfile">
            <Save :size="18" />{{ savingProfile ? "保存中" : "保存宝宝资料" }}
          </button>
        </view>
      </view>

      <view class="section settings-section">
        <view class="section-title"><text class="section-title__text">记录偏好</text></view>
        <view class="settings-form surface">
          <view class="field">
            <text class="field__label">默认喂奶间隔（分钟）</text>
            <input v-model.number="setting.defaultFeedingIntervalMin" class="field__control" type="number" />
          </view>
          <view class="toggle-row">
            <view><text>提醒声音</text></view>
            <switch :checked="setting.reminderSoundEnabled" color="#216454" @change="setting.reminderSoundEnabled = switchValue($event)" />
          </view>
          <view class="toggle-row">
            <view><text>振动提醒</text></view>
            <switch :checked="setting.reminderVibrateEnabled" color="#216454" @change="setting.reminderVibrateEnabled = switchValue($event)" />
          </view>
          <button class="btn btn--secondary settings-save" :disabled="savingSettings" @click="saveSetting">
            <Save :size="18" />{{ savingSettings ? "保存中" : "保存记录偏好" }}
          </button>
        </view>
      </view>

      <view class="section settings-section">
        <view class="section-title">
          <text class="section-title__text">关键日期</text>
          <button v-if="configured" class="btn btn--ghost" @click="openMilestone()"><Plus :size="17" />新增</button>
        </view>
        <view v-if="milestones.length" class="milestone-list surface">
          <view v-for="item in milestones" :key="`${item.code}-${item.id || item.targetTime}`" class="milestone-row">
            <view class="milestone-row__icon"><CalendarDays :size="18" /></view>
            <view class="milestone-row__body">
              <view class="milestone-row__title-line">
                <text class="milestone-row__title">{{ item.title }}</text>
                <text class="milestone-row__status">{{ milestoneStatus(item.daysDifference) }}</text>
              </view>
              <text class="muted milestone-row__date">{{ formatDateTime(item.targetTime) }}</text>
            </view>
            <view v-if="!item.system" class="milestone-row__actions">
              <button class="icon-btn" aria-label="编辑纪念日" @click="openMilestone(item)"><Pencil :size="17" /></button>
              <button class="icon-btn danger-text" aria-label="删除纪念日" @click="removeMilestone(item)"><Trash2 :size="17" /></button>
            </view>
          </view>
        </view>
        <view v-else class="surface empty-state">保存宝宝资料后可管理关键日期</view>
      </view>
    </template>

    <view v-if="milestoneModal" class="modal-backdrop" @click.self="milestoneModal = false">
      <view class="milestone-modal surface">
        <view class="modal-head">
          <text>{{ milestoneId ? "编辑纪念日" : "新增纪念日" }}</text>
          <button class="icon-btn" aria-label="关闭" @click="milestoneModal = false"><X :size="20" /></button>
        </view>
        <view class="field">
          <text class="field__label">名称</text>
          <input v-model="milestoneForm.title" class="field__control" maxlength="100" />
        </view>
        <view class="field">
          <text class="field__label">目标时间</text>
          <DateTimeField v-model="milestoneForm.targetTime" />
        </view>
        <view class="field">
          <text class="field__label">备注</text>
          <textarea v-model="milestoneForm.note" class="field__control" maxlength="500" />
        </view>
        <button class="btn btn--primary settings-save" @click="saveMilestone"><Save :size="18" />保存</button>
      </view>
    </view>

    <AppNav current="settings" />
  </view>
</template>

<style scoped>
.settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
}

.settings-logout {
  color: #8e3c39;
  border: 1px solid #ead4d2;
  background: #ffffff;
}

.settings-retry {
  margin: 12px auto 0;
}

.settings-section {
  margin-top: 22px;
}

.settings-form {
  padding: 16px;
}

.settings-form .field:last-of-type {
  margin-bottom: 16px;
}

.picker-control {
  display: flex;
  align-items: center;
}

.settings-save {
  width: 100%;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  border-top: 1px solid #edf0ee;
}

.toggle-row + .toggle-row {
  border-bottom: 1px solid #edf0ee;
}

.toggle-row:last-of-type {
  margin-bottom: 16px;
}

.milestone-list {
  overflow: hidden;
}

.milestone-row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 66px;
  padding: 10px 10px 10px 12px;
  border-bottom: 1px solid #edf0ee;
}

.milestone-row:last-child { border-bottom: 0; }

.milestone-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  color: #6c5716;
  background: #f7efc9;
}

.milestone-row__body { min-width: 0; }

.milestone-row__title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.milestone-row__title {
  overflow: hidden;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.milestone-row__status {
  flex: none;
  color: #826a1b;
  font-size: 11px;
}

.milestone-row__date {
  display: block;
  margin-top: 2px;
  font-size: 12px;
}

.milestone-row__actions {
  display: flex;
}

.modal-backdrop {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px;
  background: rgba(18, 26, 23, 0.42);
}

.milestone-modal {
  width: min(100%, 520px);
  max-height: 88vh;
  padding: 16px;
  overflow-y: auto;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  margin-bottom: 14px;
  font-size: 18px;
  font-weight: 750;
}

@media (min-width: 600px) {
  .modal-backdrop { align-items: center; }
}
</style>
