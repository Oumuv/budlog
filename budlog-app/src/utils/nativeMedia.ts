import { runtime } from "./runtime";

export type NativeMediaStatus = "idle" | "loading" | "playing" | "paused" | "error";

export interface NativeMediaState {
  status: NativeMediaStatus;
  trackId: string;
  currentTime: number;
  duration: number;
  loop: boolean;
  sleepDeadline: number | null;
  error: string;
}

export interface NativeMediaQueueItem {
  id: string;
  title: string;
  playlist: string;
  url: string;
}

export type NativeMediaCommand =
  | { action: "play"; queue: NativeMediaQueueItem[]; index: number; loop: boolean }
  | { action: "resume" | "pause" | "previous" | "next" | "stop" }
  | { action: "seek"; position: number }
  | { action: "setLoop"; loop: boolean }
  | { action: "setSleepTimer"; deadline: number | null };

export function isNativeMediaExpected(): boolean {
  return runtime.isAndroidApp && runtime.bridgeVersion === "1";
}

export function getNativeMediaBridge(): BudlogNativeMediaBridge | undefined {
  if (!isNativeMediaExpected() || typeof window === "undefined") {
    return undefined;
  }
  const bridge = window.BudlogNativeMedia;
  return bridge?.version === "1" && typeof bridge.dispatch === "function" ? bridge : undefined;
}

export function dispatchNativeMedia(command: NativeMediaCommand): boolean {
  const bridge = getNativeMediaBridge();
  if (!bridge) return false;
  try {
    bridge.dispatch(JSON.stringify(command));
    return true;
  } catch {
    return false;
  }
}

export function isNativeMediaState(value: unknown): value is NativeMediaState {
  if (!value || typeof value !== "object") return false;
  const state = value as Partial<NativeMediaState>;
  return (
    ["idle", "loading", "playing", "paused", "error"].includes(String(state.status))
    && typeof state.trackId === "string"
    && typeof state.currentTime === "number"
    && Number.isFinite(state.currentTime)
    && typeof state.duration === "number"
    && Number.isFinite(state.duration)
    && typeof state.loop === "boolean"
    && (state.sleepDeadline === null || (typeof state.sleepDeadline === "number" && Number.isFinite(state.sleepDeadline)))
    && typeof state.error === "string"
  );
}
