import { useTimerStore } from "../stores/timer";
import { clearWhiteNoiseCaches } from "./whiteNoiseCache";
import { clearNativeWidgetData } from "./nativeWidget";

const STORAGE_KEY = "budlog.access.password";

let sessionPassword = "";

export function getPassword(): string {
  return sessionPassword || String(uni.getStorageSync(STORAGE_KEY) || "");
}

export async function setPassword(password: string, remember: boolean): Promise<void> {
  const previous = getPassword();
  if (previous && previous !== password) {
    useTimerStore().clear();
    clearNativeWidgetData();
    await clearWhiteNoiseCaches();
  }
  sessionPassword = password;
  if (remember) uni.setStorageSync(STORAGE_KEY, password);
  else uni.removeStorageSync(STORAGE_KEY);
}

export async function clearPassword(): Promise<void> {
  useTimerStore().clear();
  clearNativeWidgetData();
  sessionPassword = "";
  uni.removeStorageSync(STORAGE_KEY);
  await clearWhiteNoiseCaches();
}

export function redirectToAccess(): void {
  const pages = getCurrentPages();
  const current = pages.length ? pages[pages.length - 1].route : "";
  if (current !== "pages/access/index") uni.reLaunch({ url: "/pages/access/index" });
}
