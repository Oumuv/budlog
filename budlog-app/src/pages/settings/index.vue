<script setup lang="ts">
import { CalendarDays, Check, ChevronRight, Download, LogOut, MonitorSmartphone, Pencil, Plus, Save, Trash2, X } from "lucide-vue-next";
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
const profileEditing = ref(false);
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
    profileEditing.value = !dashboard.configured;
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
    profileEditing.value = false;
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
    confirmColor: "#e84d6f",
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

function profileBirthText() {
  const local = toLocalInput(toIso(profile.birthTime, SHANGHAI_TIMEZONE), SHANGHAI_TIMEZONE);
  const [date] = local.split("T");
  const [year, month, day] = date.split("-");
  return `${year}年${month}月${day}日`;
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
      </view>

      <AppLoading v-if="loading" class="settings-state" copy="正在加载设置" />
      <ErrorState v-else-if="error" class="settings-state" title="设置加载失败" :copy="error" @retry="load" />

      <template v-else>
        <view class="section settings-section profile-section">
          <view class="section-title">
            <text class="section-title__text">宝宝资料</text>
            <button v-if="configured && !profileEditing" class="settings-add" @click="profileEditing = true">编辑 <ChevronRight :size="14" /></button>
          </view>
          <view v-if="configured && !profileEditing" class="profile-card surface">
            <image src="/static/ui/baby-avatar.png" mode="aspectFill" />
            <view class="profile-card__body">
              <view class="profile-card__row"><text>昵称</text><strong>{{ profile.name }}</strong></view>
              <view class="profile-card__row"><text>出生时间</text><strong>{{ profileBirthText() }}</strong></view>
              <view class="profile-card__row"><text>备注</text><strong>{{ profile.note || "可爱的小宝贝 ♥" }}</strong></view>
            </view>
          </view>
          <view v-else class="profile-editor surface">
            <view class="field"><text class="field__label">昵称</text><NInput v-model:value="profile.name" clearable :maxlength="50" placeholder="宝宝昵称" /></view>
            <view class="field"><text class="field__label">出生时间</text><DateTimeField v-model="profile.birthTime" title="选择出生时间" :max-now-offset-minutes="0" /></view>
            <view class="field"><text class="field__label">备注</text><NInput v-model:value="profile.note" type="textarea" :maxlength="500" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="可选" /></view>
            <view class="profile-editor__actions">
              <NButton v-if="configured" size="large" @click="profileEditing = false">取消</NButton>
              <NButton type="primary" size="large" :loading="savingProfile" @click="saveProfile"><Save :size="17" />{{ savingProfile ? "保存中" : "保存" }}</NButton>
            </view>
          </view>
        </view>

        <view class="section settings-section">
          <view class="section-title"><text class="section-title__text">记录偏好</text></view>
          <view class="preference-card surface">
            <view class="preference-field">
              <text class="preference-field__label">默认喂奶间隔（分钟）</text>
              <NInputNumber v-model:value="setting.defaultFeedingIntervalMin" :min="30" :max="720" :step="30" :precision="0" button-placement="both" />
              <view class="interval-presets">
                <button v-for="option in intervalPresets" :key="option.value" class="preset-button" :class="{ 'preset-button--active': Number(setting.defaultFeedingIntervalMin) === option.value }" @click="setFeedingInterval(option.value)">{{ option.label }}</button>
              </view>
            </view>
            <view class="toggle-row">
              <view><text class="toggle-row__title">提醒声音</text><text class="toggle-row__copy">任务到期时播放提示音</text></view>
              <NSwitch v-model:value="setting.reminderSoundEnabled" />
            </view>
            <view class="toggle-row">
              <view><text class="toggle-row__title">振动提醒</text><text class="toggle-row__copy">支持时同步触发设备振动</text></view>
              <NSwitch v-model:value="setting.reminderVibrateEnabled" />
            </view>
            <NButton class="preference-save" type="primary" secondary block :loading="savingSettings" @click="saveSetting"><Save :size="16" />{{ savingSettings ? "保存中" : "保存偏好" }}</NButton>
          </view>
        </view>

        <view class="section settings-section">
          <view class="section-title">
            <text class="section-title__text">关键日期</text>
            <button v-if="configured" class="settings-add" @click="openMilestone()"><Plus :size="15" />新增</button>
          </view>
          <view v-if="milestones.length" class="milestone-list surface">
            <view v-for="item in milestones" :key="`${item.code}-${item.id || item.targetTime}`" class="milestone-row">
              <view class="milestone-row__icon"><CalendarDays :size="17" /></view>
              <view class="milestone-row__body"><text class="milestone-row__title">{{ item.title }}</text><text class="milestone-row__date">{{ formatDate(item.targetTime) }}</text></view>
              <text class="milestone-row__status">{{ milestoneStatus(item.daysDifference) }}</text>
              <view v-if="!item.system" class="milestone-row__actions">
                <button aria-label="编辑纪念日" title="编辑纪念日" @click="openMilestone(item)"><Pencil :size="14" /></button>
                <button aria-label="删除纪念日" title="删除纪念日" @click="removeMilestone(item)"><Trash2 :size="14" /></button>
              </view>
            </view>
          </view>
          <view v-else class="state-panel surface milestone-empty">
            <view class="state-panel__icon milestone-empty__icon"><CalendarDays :size="22" /></view>
            <text class="state-panel__title">{{ configured ? "还没有关键日期" : "先保存宝宝资料" }}</text>
            <text class="state-panel__copy">{{ configured ? "添加纪念日后会在首页显示临近提醒" : "宝宝资料保存后即可管理关键日期" }}</text>
          </view>
        </view>

        <view class="section settings-section utility-section">
          <view class="section-title"><text class="section-title__text">其他</text></view>
          <view class="utility-list surface">
            <button class="utility-row" @click="installPwa">
              <view class="utility-row__icon"><MonitorSmartphone :size="19" /></view>
              <view><text>桌面应用</text><small>{{ pwaStandalone ? "已安装" : pwaInstallAvailable ? "可以安装" : "添加到主屏幕" }}</small></view>
              <view v-if="pwaStandalone" class="utility-row__state"><Check :size="14" />已安装</view>
              <Download v-else :size="17" />
            </button>
            <button class="utility-row utility-row--logout" @click="logout"><view class="utility-row__icon"><LogOut :size="19" /></view><view><text>退出访问</text><small>清除当前设备保存的访问凭据</small></view></button>
          </view>
        </view>
      </template>

      <view v-if="milestoneModal" class="modal-backdrop" @click.self="milestoneModal = false">
        <view class="milestone-modal surface">
          <view class="modal-head"><text>{{ milestoneId ? "编辑纪念日" : "新增纪念日" }}</text><button class="icon-btn" aria-label="关闭" title="关闭" @click="milestoneModal = false"><X :size="20" /></button></view>
          <view class="field"><text class="field__label">名称</text><NInput v-model:value="milestoneForm.title" clearable :maxlength="100" placeholder="例如：百日纪念" /></view>
          <view class="field"><text class="field__label">目标时间</text><DateTimeField v-model="milestoneForm.targetTime" /></view>
          <view class="field"><text class="field__label">备注</text><NInput v-model:value="milestoneForm.note" type="textarea" :maxlength="500" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" /></view>
          <NButton type="primary" size="large" block @click="saveMilestone"><Save :size="18" />保存</NButton>
        </view>
      </view>

      <AppNav current="settings" />
    </view>
  </AppPage>
</template>

<style scoped>
.settings-page { padding-top: max(22px, env(safe-area-inset-top)); }
.settings-head { display: flex; min-height: 54px; align-items: flex-start; justify-content: space-between; margin-bottom: 5px; padding: 0 5px; }
.settings-state { margin-top: 12px; }
.settings-section { margin-top: 16px; }
.profile-section { margin-top: 8px; }
.settings-add { display: inline-flex; min-height: 30px; align-items: center; gap: 2px; margin: 0; padding: 0 2px; border: 0; color: var(--bud-color-primary); background: transparent; font-size: 11px; font-weight: 700; }
.profile-card { display: grid; min-height: 124px; grid-template-columns: 78px minmax(0, 1fr); align-items: center; gap: 11px; padding: 13px; }
.profile-card image { width: 72px; height: 72px; border-radius: 50%; background: var(--bud-color-primary-soft); }
.profile-card__body { min-width: 0; }
.profile-card__row { display: grid; min-width: 0; grid-template-columns: 62px minmax(0, 1fr); gap: 4px; padding: 5px 0; border-bottom: 1px solid var(--bud-color-line-soft); }
.profile-card__row:last-child { border-bottom: 0; }
.profile-card__row text { color: var(--bud-color-muted); font-size: 9px; }
.profile-card__row strong { overflow: hidden; font-size: 11px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.profile-editor { padding: 14px; }
.profile-editor .field { margin-bottom: 14px; }
.profile-editor__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.profile-editor__actions > :only-child { grid-column: 1 / -1; }
.preference-card { overflow: hidden; padding: 13px 13px 0; }
.preference-field__label { display: block; margin-bottom: 7px; font-size: 11px; font-weight: 700; }
.preference-field :deep(.n-input-number) { width: 100%; }
.interval-presets { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; margin-top: 8px; }
.preset-button { min-width: 0; height: 28px; margin: 0; padding: 0 2px; border: 1px solid var(--bud-color-line); border-radius: 8px; color: var(--bud-color-muted); background: #fff; font-size: 9px; line-height: 26px; white-space: nowrap; }
.preset-button--active { border-color: var(--bud-color-primary); color: var(--bud-color-primary); background: var(--bud-color-primary-soft); font-weight: 700; }
.toggle-row { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 12px; border-top: 1px solid var(--bud-color-line-soft); }
.toggle-row:first-of-type { margin-top: 11px; }
.toggle-row__title, .toggle-row__copy { display: block; }
.toggle-row__title { font-size: 11px; font-weight: 750; }
.toggle-row__copy { margin-top: 2px; color: var(--bud-color-muted); font-size: 9px; }
.preference-save { margin: 0 0 13px; }
.milestone-list { overflow: hidden; }
.milestone-row { display: grid; min-width: 0; grid-template-columns: 33px minmax(0, 1fr) auto; align-items: center; gap: 8px; min-height: 58px; padding: 8px 10px; border-bottom: 1px solid var(--bud-color-line-soft); }
.milestone-row:last-child { border-bottom: 0; }
.milestone-row__icon { display: flex; width: 31px; height: 31px; align-items: center; justify-content: center; border-radius: 7px; color: var(--baby-orange); background: var(--baby-yellow-soft); }
.milestone-row__body { min-width: 0; }
.milestone-row__title, .milestone-row__date { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.milestone-row__title { font-size: 11px; font-weight: 750; }
.milestone-row__date { margin-top: 2px; color: var(--bud-color-muted); font-size: 9px; }
.milestone-row__status { padding: 3px 5px; border-radius: 5px; color: #bd7504; background: var(--baby-yellow-soft); font-size: 8px; white-space: nowrap; }
.milestone-row__actions { display: flex; grid-column: 2 / -1; justify-content: flex-end; gap: 2px; margin-top: -5px; }
.milestone-row__actions button { display: flex; width: 25px; height: 25px; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 6px; color: var(--bud-color-muted); background: transparent; }
.milestone-row__actions button:last-child { color: var(--bud-color-primary); }
.milestone-empty { min-height: 165px; }
.milestone-empty__icon { color: var(--baby-orange); background: var(--baby-yellow-soft); }
.utility-section { margin-bottom: 12px; }
.utility-list { overflow: hidden; }
.utility-row { display: grid; width: 100%; min-height: 62px; grid-template-columns: 35px minmax(0, 1fr) auto; align-items: center; gap: 8px; margin: 0; padding: 9px 11px; border-bottom: 1px solid var(--bud-color-line-soft); text-align: left; }
.utility-row:last-child { border-bottom: 0; }
.utility-row__icon { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; border-radius: 7px; color: var(--baby-blue); background: var(--baby-blue-soft); }
.utility-row text, .utility-row small { display: block; }
.utility-row text { font-size: 11px; font-weight: 750; }
.utility-row small { margin-top: 2px; color: var(--bud-color-muted); font-size: 9px; }
.utility-row__state { display: flex; align-items: center; gap: 3px; color: var(--baby-green); font-size: 9px; }
.utility-row--logout .utility-row__icon { color: var(--bud-color-primary); background: var(--bud-color-primary-soft); }
.modal-backdrop { position: fixed; z-index: 50; inset: 0; display: flex; align-items: flex-end; justify-content: center; padding: 15px; background: rgba(31, 37, 53, 0.48); backdrop-filter: blur(3px); }
.milestone-modal { width: min(100%, 430px); max-height: 88vh; overflow-y: auto; padding: 17px; }
.modal-head { display: flex; min-height: 40px; align-items: center; justify-content: space-between; margin-bottom: 13px; font-size: 17px; font-weight: 800; }
@media (min-width: 600px) { .modal-backdrop { align-items: center; } }
</style>
