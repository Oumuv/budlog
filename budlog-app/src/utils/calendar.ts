import type { CalendarCategory, CalendarItem } from "../types";
import { shiftDay } from "./date";

export type CalendarFilter = "ALL" | "TASK" | "MILESTONE" | "EVENT" | "CARE";

export const calendarFilters: Array<{ value: CalendarFilter; label: string }> = [
  { value: "ALL", label: "全部" },
  { value: "TASK", label: "任务日程" },
  { value: "MILESTONE", label: "关键节点" },
  { value: "EVENT", label: "事件" },
  { value: "CARE", label: "日常记录" },
];

export const categoryLabels: Record<CalendarCategory, string> = {
  TASK: "任务日程", MILESTONE: "关键节点", EVENT: "事件记录",
  FEEDING: "喂奶", DIAPER: "尿便", MILK_STORAGE: "存奶", WEIGHT: "体重",
};

export const eventLabels = { VACCINE: "疫苗", DOCUMENT: "证件", MOMENT: "小事", OTHER: "其他" };
export const taskLabels = { TODO: "待处理", DONE: "已完成", CANCELED: "已取消" };

export function calendarGroup(category: CalendarCategory): Exclude<CalendarFilter, "ALL"> {
  return category === "TASK" || category === "MILESTONE" || category === "EVENT" ? category : "CARE";
}

export function monthGrid(month: string): string[] {
  const first = `${month}-01`;
  const [year, monthNumber] = month.split("-").map(Number);
  const weekday = new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay();
  const daysInMonth = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const start = shiftDay(first, -weekday);
  const cellCount = weekday + daysInMonth <= 35 ? 35 : 42;
  return Array.from({ length: cellCount }, (_, index) => shiftDay(start, index));
}

export function shiftMonth(month: string, amount: number): string {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, monthNumber - 1 + amount, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function calendarDetailUrl(item: CalendarItem): string {
  const identity = item.id == null ? `code=${encodeURIComponent(item.recordType)}` : `id=${item.id}`;
  return `/pages/calendar-detail/index?category=${item.category}&${identity}`;
}

export function calendarItemLabel(item: CalendarItem): string {
  if (item.category === "TASK") {
    return item.overdue ? "已逾期" : taskLabels[item.recordType as keyof typeof taskLabels] || "任务";
  }
  if (item.category === "EVENT") return eventLabels[item.recordType as keyof typeof eventLabels] || "事件";
  return categoryLabels[item.category];
}
