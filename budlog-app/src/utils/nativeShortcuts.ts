const ALLOWED_SHORTCUT_ROUTES = new Set([
  "/pages/home/index",
  "/pages/records/index",
  "/pages/settings/index",
  "/pages/feeding/index?mode=timer",
  "/pages/feeding/index?mode=bottle",
  "/pages/milk-storage/index",
  "/pages/diaper/index",
  "/pages/task-edit/index",
  "/pages/white-noise/index",
]);

let pendingShortcutRoute = "";

export function queueNativeShortcut(value: unknown): boolean {
  if (!value || typeof value !== "object") return false;
  const route = (value as { route?: unknown }).route;
  if (typeof route !== "string" || !ALLOWED_SHORTCUT_ROUTES.has(route)) return false;
  pendingShortcutRoute = route;
  return true;
}

export function consumePendingNativeShortcut(): string {
  const route = pendingShortcutRoute;
  pendingShortcutRoute = "";
  return route;
}

export function peekPendingNativeShortcut(): string {
  return pendingShortcutRoute;
}
