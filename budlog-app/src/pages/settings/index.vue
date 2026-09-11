<script setup lang="ts">
import { CalendarDays, Check, Download, LogOut, MonitorSmartphone, Pencil, Plus, Save, Trash2, X } from "lucide-vue-next";
import { NButton, NInput, NInputNumber, NSwitch } from "naive-ui";
import { onShow } from "@dcloudio/uni-app";
import { reactive, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import DateTimeField from "../../components/DateTimeField.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { AppSetting, Milestone } from "../../types";
import { clearPassword } from "../../utils/auth";
import { formatDate, nowLocalInput, setAppTimezone, toIso, toLocalInput, uuid } from "../../utils/date";
import { ensureAccess, resetAccessVerification } from "../../utils/guard";
import { pwaInstallAvailable, pwaStandalone, requestPwaInstall } from "../../utils/pwa";

const SHANGHAI_TIMEZONE = "Asia/Shanghai";
const loading = ref(false);
const savingProfile = ref(false);
const savingSettings = ref(false);
const error = ref("");
const configured = ref(false);
const milestones = ref<Milestone[]>([]);
const profile = reactive({ name: "", birthTime: nowLocalInput(SHANGHAI_TIMEZONE), note: "" });
const setting = reactive<AppSetting>({
  defaultFeedingIntervalMin: 180,
  feedingIntervalAnchor: "START",
  reminderSoundEnabled: false,
  reminderVibrateEnabled: true,
});
const milestoneModal = ref(false);
const milestoneId = ref<number>();
const milestoneForm = reactive({ clientRequestId: uuid(), title: "", targetTime: nowLocalInput(SHANGHAI_TIMEZONE), note: "" });
const intervalPresets = [
  { value: 120, label: "2 小时" },
  { value: 150, label: "2.5 小时" },
  { value: 180, label: "3 小时" },
  { value: 240, label: "4 小时" },
];
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
    setAppTimezone(SHANGHAI_TIMEZONE);
    if (dashboard.baby) {
      profile.name = dashboard.baby.name;
      profile.birthTime = toLocalInput(dashboard.baby.birthTime, SHANGHAI_TIMEZONE);
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
      birthTime: toIso(profile.birthTime, SHANGHAI_TIMEZONE),
      timezone: SHANGHAI_TIMEZONE,
      note: profile.note.trim() || undefined,
    });
    setAppTimezone(SHANGHAI_TIMEZONE);
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
  const interval = Number(setting.defaultFeedingIntervalMin);
  if (!Number.isInteger(interval) || interval < 30 || interval > 720) {
    uni.showToast({ title: "喂奶间隔应为 30 到 720 分钟", icon: "none" });
    return;
  }
  savingSettings.value = true;
  try {
    Object.assign(
      setting,
      await api.saveSettings({
        defaultFeedingIntervalMin: interval,
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
    ? toLocalInput(item.targetTime, SHANGHAI_TIMEZONE)
    : nowLocalInput(SHANGHAI_TIMEZONE);
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
    targetTime: toIso(milestoneForm.targetTime, SHANGHAI_TIMEZONE),
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

function logout() {
  clearPassword();
  resetAccessVerification();
  uni.reLaunch({ url: "/pages/access/index" });
}

function milestoneStatus(days: number) {
  if (days === 0) return "今天";
  return days > 0 ? `${days} 天后` : `${Math.abs(days)} 天前`;
}

function setFeedingInterval(value: number) {
  setting.defaultFeedingIntervalMin = value;
}

async function installPwa() {
  try {
    const result = await requestPwaInstall();
    if (result === "accepted") {
      uni.showToast({ title: "安装已开始", icon: "success" });
      return;
    }
    if (result === "dismissed") {
      uni.showToast({ title: "已取消安装", icon: "none" });
      return;
    }
    if (result === "manual") {
      uni.showModal({
        title: "添加到桌面",
        content: "请打开浏览器菜单，选择“安装应用”或“添加到主屏幕”。iPhone 和 iPad 请使用 Safari 的分享菜单。",
        showCancel: false,
      });
    }
  } catch (exception) {
    console.error("Budlog install prompt failed", exception);
    uni.showToast({ title: "暂时无法唤起安装，请通过浏览器菜单添加", icon: "none" });
  }
}
</script>

<template>
  <AppPage>
    <view class="page-shell settings-page">
    <view class="settings-head">
      <view>
        <text class="page-title">设置</text>
        <text class="page-subtitle">管理宝宝资料、提醒和关键日期</text>
      </view>
      <button class="icon-btn settings-logout" aria-label="退出访问" title="退出访问" @click="logout"><LogOut :size="20" /></button>
    </view>

    <AppLoading v-if="loading" class="settings-state" copy="正在加载设置" />
    <ErrorState v-else-if="error" class="settings-state" title="设置加载失败" :copy="error" @retry="load" />

    <template v-else>
      <view class="section settings-section">
        <view class="section-title"><text class="section-title__text">宝宝资料</text></view>
        <view class="settings-form surface">
          <view class="field">
            <text class="field__label">昵称</text>
            <NInput v-model:value="profile.name" clearable :maxlength="50" placeholder="宝宝昵称" />
          </view>
          <view class="field">
            <text class="field__label">出生时间</text>
            <DateTimeField v-model="profile.birthTime" title="选择出生时间" :max-now-offset-minutes="0" />
          </view>
          <view class="field">
            <text class="field__label">备注</text>
            <NInput v-model:value="profile.note" type="textarea" :maxlength="500" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" />
          </view>
          <NButton type="primary" size="large" block :loading="savingProfile" @click="saveProfile">
            <Save :size="18" />{{ savingProfile ? "保存中" : "保存宝宝资料" }}
          </NButton>
        </view>
      </view>

      <view class="section settings-section">
        <view class="section-title"><text class="section-title__text">记录偏好</text></view>
        <view class="settings-form surface">
          <view class="field">
            <text class="field__label">默认喂奶间隔（分钟）</text>
            <view class="interval-stepper">
              <NInputNumber
                v-model:value="setting.defaultFeedingIntervalMin"
                :min="30"
                :max="720"
                :step="30"
                :precision="0"
                button-placement="both"
              />
              <text class="interval-stepper__unit">分钟</text>
            </view>
            <view class="shortcut-row interval-presets">
              <button
                v-for="option in intervalPresets"
                :key="option.value"
                class="shortcut-chip"
                :class="{ 'shortcut-chip--active': Number(setting.defaultFeedingIntervalMin) === option.value }"
                @click="setFeedingInterval(option.value)"
              >
                {{ option.label }}
              </button>
            </view>
          </view>
          <view class="toggle-row">
            <view>
              <text class="toggle-row__title">提醒声音</text>
              <text class="toggle-row__copy">任务到期时播放提示音</text>
            </view>
            <NSwitch v-model:value="setting.reminderSoundEnabled" />
          </view>
          <view class="toggle-row">
            <view>
              <text class="toggle-row__title">振动提醒</text>
              <text class="toggle-row__copy">支持时同步触发设备振动</text>
            </view>
            <NSwitch v-model:value="setting.reminderVibrateEnabled" />
          </view>
          <NButton type="primary" size="large" secondary block :loading="savingSettings" @click="saveSetting">
            <Save :size="18" />{{ savingSettings ? "保存中" : "保存记录偏好" }}
          </NButton>
        </view>
      </view>

      <view class="section settings-section">
        <view class="section-title"><text class="section-title__text">桌面应用</text></view>
        <view class="pwa-install surface">
          <view class="pwa-install__icon"><MonitorSmartphone :size="21" /></view>
          <view class="pwa-install__body">
            <text class="pwa-install__title">Budlog</text>
            <text class="pwa-install__status">{{ pwaStandalone ? "桌面模式" : pwaInstallAvailable ? "可以安装" : "添加到主屏幕" }}</text>
          </view>
          <view v-if="pwaStandalone" class="pwa-install__installed"><Check :size="16" />已安装</view>
          <NButton v-else type="primary" size="medium" secondary @click="installPwa">
            <Download :size="17" />安装
          </NButton>
        </view>
      </view>

      <view class="section settings-section">
        <view class="section-title">
          <text class="section-title__text">关键日期</text>
          <button v-if="configured" class="settings-add" @click="openMilestone()"><Plus :size="16" />新增</button>
        </view>
        <view v-if="milestones.length" class="milestone-list surface">
          <view v-for="item in milestones" :key="`${item.code}-${item.id || item.targetTime}`" class="milestone-row">
            <view class="milestone-row__icon"><CalendarDays :size="18" /></view>
            <view class="milestone-row__body">
              <view class="milestone-row__title-line">
                <text class="milestone-row__title">{{ item.title }}</text>
                <text class="milestone-row__status">{{ milestoneStatus(item.daysDifference) }}</text>
              </view>
              <text class="muted milestone-row__date">{{ formatDate(item.targetTime) }}</text>
            </view>
            <view v-if="!item.system" class="milestone-row__actions">
              <button class="icon-btn" aria-label="编辑纪念日" title="编辑纪念日" @click="openMilestone(item)"><Pencil :size="17" /></button>
              <button class="icon-btn danger-text" aria-label="删除纪念日" title="删除纪念日" @click="removeMilestone(item)"><Trash2 :size="17" /></button>
            </view>
          </view>
        </view>
        <view v-else class="state-panel surface milestone-empty">
          <view class="state-panel__icon milestone-empty__icon"><CalendarDays :size="22" /></view>
          <text class="state-panel__title">{{ configured ? "还没有关键日期" : "先保存宝宝资料" }}</text>
          <text class="state-panel__copy">{{ configured ? "添加纪念日后会在首页显示临近提醒" : "宝宝资料保存后即可管理关键日期" }}</text>
        </view>
      </view>
    </template>

    <view v-if="milestoneModal" class="modal-backdrop" @click.self="milestoneModal = false">
      <view class="milestone-modal surface">
        <view class="modal-head">
          <text>{{ milestoneId ? "编辑纪念日" : "新增纪念日" }}</text>
          <button class="icon-btn" aria-label="关闭" title="关闭" @click="milestoneModal = false"><X :size="20" /></button>
        </view>
        <view class="field">
          <text class="field__label">名称</text>
          <NInput v-model:value="milestoneForm.title" clearable :maxlength="100" placeholder="例如：百日纪念" />
        </view>
        <view class="field">
          <text class="field__label">目标时间</text>
          <DateTimeField v-model="milestoneForm.targetTime" />
        </view>
        <view class="field">
          <text class="field__label">备注</text>
          <NInput v-model:value="milestoneForm.note" type="textarea" :maxlength="500" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" />
        </view>
        <NButton type="primary" size="large" block @click="saveMilestone"><Save :size="18" />保存</NButton>
      </view>
    </view>

    <AppNav current="settings" />
    </view>
  </AppPage>
</template>

<style scoped>
.settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-bottom: 20px;
}

.settings-logout {
  color: #a33e43;
  border: 1px solid #efd4d6;
  background: #ffffff;
  box-shadow: var(--bud-shadow-sm);
}

.settings-state {
  margin-top: 4px;
}

.settings-error {
  color: #a33e43;
  background: var(--bud-color-coral-soft);
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

.interval-stepper {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--bud-color-line);
  border-radius: 10px;
  background: #fffdfd;
}

.interval-stepper__unit {
  color: var(--bud-color-muted);
  font-size: 13px;
  font-weight: 650;
}

.interval-presets {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.interval-presets .shortcut-chip {
  width: 100%;
  padding-right: 5px;
  padding-left: 5px;
}

.pwa-install {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  min-height: 76px;
  padding: 13px 14px;
  align-items: center;
  gap: 11px;
}

.pwa-install__icon {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.pwa-install__body,
.pwa-install__title,
.pwa-install__status {
  display: block;
  min-width: 0;
}

.pwa-install__title {
  color: var(--bud-color-ink);
  font-size: 15px;
  font-weight: 700;
}

.pwa-install__status {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.pwa-install__installed {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--bud-color-sage);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  gap: 16px;
  border-top: 1px solid var(--bud-color-line-soft);
}

.toggle-row + .toggle-row {
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.toggle-row__title,
.toggle-row__copy {
  display: block;
}

.toggle-row__title {
  color: var(--bud-color-ink);
  font-size: 14px;
  font-weight: 650;
}

.toggle-row__copy {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
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
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.milestone-row:last-child { border-bottom: 0; }

.milestone-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 7px;
  color: var(--bud-color-gold);
  background: var(--bud-color-gold-soft);
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
  color: var(--bud-color-gold);
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

.settings-add {
  display: inline-flex;
  min-height: 34px;
  margin: 0;
  padding: 0 4px;
  align-items: center;
  gap: 3px;
  border: 0;
  color: var(--bud-color-primary);
  background: transparent;
  font-size: 13px;
  font-weight: 650;
}

.milestone-empty {
  min-height: 188px;
}

.milestone-empty__icon {
  color: var(--bud-color-gold);
  background: var(--bud-color-gold-soft);
}

.modal-backdrop {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px;
  background: rgba(55, 37, 41, 0.48);
  backdrop-filter: blur(3px);
}

.milestone-modal {
  width: min(100%, 520px);
  max-height: 88vh;
  padding: 18px;
  overflow-y: auto;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  margin-bottom: 14px;
  font-size: 18px;
  font-weight: 760;
}

@media (min-width: 600px) {
  .modal-backdrop { align-items: center; }
}
</style>
