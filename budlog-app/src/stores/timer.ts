import { defineStore } from "pinia";
import type { BreastSide } from "../types";
import { uuid } from "../utils/date";
import { clearNativeWidgetTimer, syncNativeWidgetTimer } from "../utils/nativeWidget";

const STORAGE_KEY = "budlog.feeding.timer";

interface TimerDraft {
  clientRequestId: string;
  startTime: string;
  breastSide: BreastSide;
}

export const useTimerStore = defineStore("feedingTimer", {
  state: () => ({ draft: (uni.getStorageSync(STORAGE_KEY) || null) as TimerDraft | null }),
  actions: {
    start(breastSide: BreastSide) {
      this.draft = { clientRequestId: uuid(), startTime: new Date().toISOString(), breastSide };
      uni.setStorageSync(STORAGE_KEY, this.draft);
      syncNativeWidgetTimer(this.draft.startTime, this.draft.breastSide);
    },
    clear() {
      this.draft = null;
      uni.removeStorageSync(STORAGE_KEY);
      clearNativeWidgetTimer();
    },
    syncNative() {
      if (this.draft) syncNativeWidgetTimer(this.draft.startTime, this.draft.breastSide);
      else clearNativeWidgetTimer();
    },
    elapsedSeconds(now = Date.now()) {
      return this.draft ? Math.max(0, Math.floor((now - new Date(this.draft.startTime).getTime()) / 1000)) : 0;
    },
  },
});
