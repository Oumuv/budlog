<script setup lang="ts">
import {
  Baby,
  BellRing,
  CalendarDays,
  ChevronRight,
  Droplets,
  Milk,
  NotebookPen,
  PackagePlus,
  Scale,
  SquareCheckBig,
  Timer,
} from "lucide-vue-next";
import { NButton } from "naive-ui";
import { onShow } from "@dcloudio/uni-app";
import { computed, ref } from "vue";
import { api } from "../../api";
import AppLoading from "../../components/AppLoading.vue";
import AppNav from "../../components/AppNav.vue";
import AppPage from "../../components/AppPage.vue";
import ErrorState from "../../components/ErrorState.vue";
import type { Dashboard } from "../../types";
import { formatDate, setAppTimezone, toLocalInput } from "../../utils/date";
import { ensureAccess } from "../../utils/guard";

const dashboard = ref<Dashboard>();
const loading = ref(true);
const error = ref("");

const calendarAgeDays = computed(() => Math.max(1, dashboard.value?.baby?.ageDayNumber ?? 1));
const highlightedMilestones = computed(() =>
  [...(dashboard.value?.milestones ?? [])]
    .filter((item) => item.daysDifference >= 0)
    .sort((a, b) => a.daysDifference - b.daysDifference)
    .slice(0, 2),
);

onShow(async () => {
  if (!(await ensureAccess())) return;
  await load();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    dashboard.value = await api.dashboard();
    if (dashboard.value.baby) setAppTimezone(dashboard.value.baby.timezone);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "首页加载失败";
  } finally {
    loading.value = false;
  }
}

function go(url: string) {
  uni.navigateTo({ url });
}

function redirect(url: string) {
  uni.redirectTo({ url });
}

function milestoneLabel(days: number) {
  if (days === 0) return "今天";
  return days > 0 ? `还有 ${days} 天` : `已过 ${Math.abs(days)} 天`;
}

function birthDate(value: string) {
  return toLocalInput(value).slice(0, 10);
}
</script>

<template>
  <AppPage>
    <view class="page-shell home-page">
      <view class="home-head">
        <view>
          <text class="home-head__brand">Budlog</text>
          <text class="home-head__subtitle">记录宝宝的每一天 <text class="home-head__heart">♥</text></text>
        </view>
        <view class="home-head__actions">
          <button class="icon-btn home-head__bell" aria-label="查看任务" title="查看任务" @click="redirect('/pages/tasks/index')">
            <BellRing :size="20" />
            <text v-if="dashboard?.tasks.length" class="home-head__badge" />
          </button>
          <image class="home-head__avatar" src="/static/ui/baby-avatar.png" mode="aspectFill" />
        </view>
      </view>

      <AppLoading v-if="loading" copy="正在整理今天的记录" />
      <ErrorState v-else-if="error" title="首页暂时无法加载" :copy="error" @retry="load" />
      <view v-else-if="dashboard && !dashboard.configured" class="state-panel surface setup-state">
        <view class="state-panel__icon"><Baby :size="22" /></view>
        <text class="state-panel__title">先添加宝宝资料</text>
        <text class="state-panel__copy">完成昵称和出生时间设置后，即可开始记录日常</text>
        <NButton type="primary" size="large" @click="redirect('/pages/settings/index')">开始设置</NButton>
      </view>

      <template v-else-if="dashboard?.baby">
        <view class="baby-card surface">
          <view class="baby-card__hero">
            <view class="baby-card__copy">
              <text class="baby-card__eyebrow">嗨，宝贝</text>
              <text class="baby-card__name">{{ dashboard.baby.name }}</text>
              <text class="baby-card__wish">今天也在好好长大呀！</text>
            </view>
            <image class="baby-card__art" src="/static/ui/baby-hero.png" mode="aspectFit" />
          </view>
          <view class="baby-card__age">
            <view class="baby-card__age-item">
              <text class="baby-card__age-value">{{ calendarAgeDays }} 天</text>
              <text class="baby-card__age-label">已出生</text>
            </view>
            <view class="baby-card__age-item">
              <text class="baby-card__age-value">{{ dashboard.baby.ageMonths }} 个月 {{ dashboard.baby.ageRemainingDays }} 天</text>
              <text class="baby-card__age-label">自然月龄</text>
            </view>
          </view>
          <view class="baby-card__birth">
            <CalendarDays :size="16" />
            <text>{{ birthDate(dashboard.baby.birthTime) }} 出生</text>
          </view>
        </view>

        <view v-if="highlightedMilestones.length" class="milestone-grid">
          <button
            v-for="(item, index) in highlightedMilestones"
            :key="`${item.code}-${item.id || item.targetTime}`"
            class="milestone-card surface"
            :class="{ 'milestone-card--green': index === 1 }"
            @click="go('/pages/calendar/index')"
          >
            <view class="milestone-card__icon"><CalendarDays :size="20" /></view>
            <view class="milestone-card__body">
              <text class="milestone-card__title">{{ item.title }}</text>
              <text class="milestone-card__days">{{ milestoneLabel(item.daysDifference) }}</text>
              <text class="milestone-card__date">{{ formatDate(item.targetTime) }}</text>
            </view>
          </button>
        </view>

        <view class="quick-grid">
          <button class="quick-action quick-action--pink" @click="go('/pages/feeding/index?mode=timer')"><Timer :size="23" /><text>喂奶</text></button>
          <button class="quick-action quick-action--blue" @click="go('/pages/feeding/index?mode=bottle')"><Milk :size="23" /><text>瓶喂</text></button>
          <button class="quick-action quick-action--green" @click="go('/pages/milk-storage/index')"><PackagePlus :size="23" /><text>存奶</text></button>
          <button class="quick-action quick-action--yellow" @click="go('/pages/diaper/index')"><Droplets :size="23" /><text>尿便</text></button>
          <button class="quick-action quick-action--purple" @click="go('/pages/weight/index')"><Scale :size="23" /><text>体重</text></button>
          <button class="quick-action quick-action--cyan" @click="go('/pages/event/index')"><NotebookPen :size="23" /><text>事件</text></button>
          <button class="quick-action quick-action--rose" @click="go('/pages/task-edit/index')"><SquareCheckBig :size="23" /><text>任务</text></button>
          <button class="quick-action quick-action--mint" @click="go('/pages/calendar/index')"><CalendarDays :size="23" /><text>日历</text></button>
        </view>

        <view class="section today-section">
          <view class="section-title">
            <text class="section-title__text">今日摘要</text>
            <button class="section-link" @click="redirect('/pages/records/index')">查看更多 <ChevronRight :size="14" /></button>
          </view>
          <view class="summary-card surface">
            <view class="summary-card__metric summary-card__metric--pink"><Milk :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.feedingCount }}</text><text class="summary-card__label">喂奶</text></view>
            <view class="summary-card__metric summary-card__metric--blue"><Droplets :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.bottleAmountMl }}</text><text class="summary-card__label">毫升</text></view>
            <view class="summary-card__metric summary-card__metric--slate"><Timer :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.directFeedingMinutes }}</text><text class="summary-card__label">亲喂(分钟)</text></view>
            <view class="summary-card__metric summary-card__metric--yellow"><Baby :size="18" /><text class="summary-card__value">{{ dashboard.todaySummary.peeCount }}/{{ dashboard.todaySummary.poopCount }}</text><text class="summary-card__label">尿/便</text></view>
          </view>
          <view class="summary-detail-grid">
            <button class="summary-detail surface" @click="go('/pages/milk-storage/index')">
              <view class="summary-detail__icon summary-detail__icon--green"><PackagePlus :size="21" /></view>
              <view><text class="summary-detail__label">存奶 {{ dashboard.todaySummary.milkStorageCount }} 次</text><text class="summary-detail__value">{{ dashboard.todaySummary.storedMilkAmountMl }} ml</text></view>
            </button>
            <button class="summary-detail surface" @click="go('/pages/weight/index')">
              <view class="summary-detail__icon summary-detail__icon--purple"><Scale :size="21" /></view>
              <view><text class="summary-detail__label">体重</text><text class="summary-detail__value">{{ dashboard.todaySummary.weightKg ?? '--' }} kg</text></view>
            </button>
          </view>
        </view>
      </template>

      <AppNav current="home" />
    </view>
  </AppPage>
</template>

<style scoped>
.home-page { padding-top: max(22px, env(safe-area-inset-top)); }
.home-head { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 15px; padding: 0 5px; }
.home-head__brand, .home-head__subtitle { display: block; }
.home-head__brand { font-size: 23px; line-height: 29px; font-weight: 850; }
.home-head__subtitle { color: var(--bud-color-muted); font-size: 12px; line-height: 18px; }
.home-head__heart { color: var(--bud-color-primary); }
.home-head__actions { display: flex; align-items: center; gap: 11px; }
.home-head__bell { position: relative; overflow: visible; }
.home-head__badge { position: absolute; top: 4px; right: 3px; width: 8px; height: 8px; border: 2px solid var(--bud-color-canvas); border-radius: 50%; background: var(--bud-color-primary); }
.home-head__avatar { width: 48px; height: 48px; border-radius: 50%; background: #fff0f4; }
.setup-state { min-height: 360px; }
.baby-card { overflow: hidden; border-color: #f8e8ef; }
.baby-card__hero { position: relative; min-height: 126px; overflow: hidden; padding: 17px 18px; background: linear-gradient(110deg, #fff3f7 0%, #ffe3ed 100%); }
.baby-card__copy { position: relative; z-index: 1; max-width: 54%; }
.baby-card__eyebrow, .baby-card__name, .baby-card__wish { display: block; }
.baby-card__eyebrow { color: #f39bb5; font-size: 11px; font-weight: 700; }
.baby-card__name { margin-top: 2px; overflow-wrap: anywhere; font-size: 26px; line-height: 34px; font-weight: 850; }
.baby-card__wish { margin-top: 3px; color: var(--bud-color-muted); font-size: 12px; white-space: nowrap; }
.baby-card__art { position: absolute; right: 0; bottom: 0; width: 145px; height: 116px; -webkit-mask-image: radial-gradient(ellipse at 72% 58%, #000 58%, transparent 88%); mask-image: radial-gradient(ellipse at 72% 58%, #000 58%, transparent 88%); }
.baby-card__age { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 13px 0 11px; }
.baby-card__age-item { min-width: 0; padding: 0 16px; }
.baby-card__age-item + .baby-card__age-item { border-left: 1px solid var(--bud-color-line-soft); }
.baby-card__age-value, .baby-card__age-label { display: block; }
.baby-card__age-value { overflow-wrap: anywhere; font-size: 17px; line-height: 24px; font-weight: 800; }
.baby-card__age-label { margin-top: 1px; color: var(--bud-color-muted); font-size: 11px; }
.baby-card__birth { display: flex; align-items: center; gap: 8px; margin: 0 15px; padding: 10px 0 12px; border-top: 1px solid var(--bud-color-line-soft); color: var(--bud-color-muted); font-size: 11px; }
.baby-card__birth .lucide { color: var(--bud-color-primary); }
.milestone-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 11px; }
.milestone-card { display: grid; min-width: 0; grid-template-columns: 32px minmax(0, 1fr); gap: 8px; margin: 0; padding: 12px 10px; text-align: left; line-height: 1.35; }
.milestone-card__icon { display: flex; width: 30px; height: 30px; align-items: center; justify-content: center; border-radius: 7px; color: var(--bud-color-primary); background: var(--bud-color-primary-soft); }
.milestone-card--green .milestone-card__icon { color: var(--baby-green); background: var(--baby-green-soft); }
.milestone-card__body { min-width: 0; }
.milestone-card__title, .milestone-card__days, .milestone-card__date { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.milestone-card__title { font-size: 13px; font-weight: 750; }
.milestone-card__days { margin-top: 2px; font-size: 12px; font-weight: 750; }
.milestone-card__date { margin-top: 1px; color: var(--bud-color-muted); font-size: 9px; }
.quick-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; margin-top: 15px; }
.quick-action { display: flex; min-width: 0; min-height: 66px; flex-direction: column; align-items: center; justify-content: center; gap: 7px; margin: 0; padding: 7px 2px; border: 1px solid transparent; border-radius: 8px; font-size: 11px; font-weight: 700; }
.quick-action--pink { color: #f64078; background: #fff0f5; border-color: #ffdbe7; }
.quick-action--blue { color: #267dea; background: #edf5ff; }
.quick-action--green { color: #1bad75; background: #eafbf4; }
.quick-action--yellow { color: #df8600; background: #fff7e7; }
.quick-action--purple { color: #7856e5; background: #f3efff; }
.quick-action--cyan { color: #168a9c; background: #eaf9fb; }
.quick-action--rose { color: #ee3b76; background: #fff0f5; }
.quick-action--mint { color: #20a975; background: #eafff5; }
.today-section { margin-top: 17px; }
.section-link { display: inline-flex; align-items: center; gap: 1px; margin: 0; padding: 4px 0; color: var(--bud-color-muted); background: transparent; font-size: 11px; line-height: 18px; }
.summary-card { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 12px 5px; }
.summary-card__metric { display: flex; min-width: 0; flex-direction: column; align-items: center; gap: 2px; padding: 1px 3px; border-right: 1px solid var(--bud-color-line-soft); }
.summary-card__metric:last-child { border-right: 0; }
.summary-card__metric--pink { color: #f64078; }
.summary-card__metric--blue { color: #267dea; }
.summary-card__metric--slate { color: #52637f; }
.summary-card__metric--yellow { color: #df8600; }
.summary-card__value { color: var(--bud-color-ink); font-size: 15px; line-height: 20px; font-weight: 800; }
.summary-card__label { max-width: 100%; overflow: hidden; color: var(--bud-color-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.summary-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.summary-detail { display: flex; min-width: 0; min-height: 76px; align-items: flex-start; gap: 9px; margin: 0; padding: 12px 10px; text-align: left; }
.summary-detail__icon { display: flex; width: 31px; height: 31px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 7px; }
.summary-detail__icon--green { color: var(--baby-green); background: var(--baby-green-soft); }
.summary-detail__icon--purple { color: var(--baby-purple); background: var(--baby-purple-soft); }
.summary-detail__label, .summary-detail__value { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-detail__label { font-size: 11px; font-weight: 700; }
.summary-detail__value { margin-top: 4px; font-size: 14px; font-weight: 800; }
@media (max-width: 350px) {
  .baby-card__art { right: -16px; }
  .baby-card__wish { white-space: normal; }
  .quick-grid { gap: 6px; }
}
</style>
