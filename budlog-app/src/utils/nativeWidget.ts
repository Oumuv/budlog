import type { BreastSide } from "../types";
import { runtime } from "./runtime";

export type NativeWidgetCommand =
  | { action: "refresh" | "clearTimer" | "requestPin" | "clearWidgetData" }
  | { action: "syncTimer"; timer: { startTime: string; breastSide: BreastSide } };

export function getNativeWidgetBridge(): BudlogNativeWidgetBridge | undefined {
  if (!runtime.isAndroidApp || typeof window === "undefined") return undefined;
  const bridge = window.BudlogNativeWidget;
  return bridge?.version === "1" && typeof bridge.dispatch === "function" ? bridge : undefined;
}

export function isNativeWidgetAvailable(): boolean {
  return Boolean(getNativeWidgetBridge());
}

export function dispatchNativeWidget(command: NativeWidgetCommand): boolean {
  const bridge = getNativeWidgetBridge();
  if (!bridge) return false;
  try {
    bridge.dispatch(JSON.stringify(command));
    return true;
  } catch {
    return false;
  }
}

export function requestNativeWidgetRefresh(): boolean {
  return dispatchNativeWidget({ action: "refresh" });
}

export function syncNativeWidgetTimer(startTime: string, breastSide: BreastSide): boolean {
  return dispatchNativeWidget({ action: "syncTimer", timer: { startTime, breastSide } });
}

export function clearNativeWidgetTimer(): boolean {
  return dispatchNativeWidget({ action: "clearTimer" });
}

export function clearNativeWidgetData(): boolean {
  return dispatchNativeWidget({ action: "clearWidgetData" });
}

export function requestPinNativeWidget(): boolean {
  return dispatchNativeWidget({ action: "requestPin" });
}
