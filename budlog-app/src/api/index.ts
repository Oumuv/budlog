import type {
  AppSetting,
  Baby,
  BabyInput,
  Dashboard,
  DiaperInput,
  DiaperRecord,
  EventInput,
  EventRecord,
  FeedingInput,
  FeedingRecord,
  MilkStorageInput,
  MilkStorageRecord,
  Milestone,
  MilestoneInput,
  PageResult,
  TaskInput,
  TaskStatus,
  Timeline,
  TodoTask,
  WeightInput,
  WeightRecord,
} from "../types";
import { toIso } from "../utils/date";
import { apiRequest } from "./request";

const query = (params: Record<string, string | number | undefined>) => {
  const values = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return values.length ? `?${values.join("&")}` : "";
};

const rangeDateTime = (value?: string) => {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  return toIso(`${value}T00:00`);
};

export const api = {
  dashboard: (date?: string) => apiRequest<Dashboard>(`/dashboard${query({ date })}`),
  timeline: (date: string) => apiRequest<Timeline>(`/timeline${query({ date })}`),
  getBaby: () => apiRequest<Baby>("/baby"),
  saveBaby: (data: BabyInput) => apiRequest<Baby>("/baby", { method: "PUT", data }),
  milestones: () => apiRequest<Milestone[]>("/milestones"),
  createMilestone: (data: MilestoneInput) => apiRequest<Milestone>("/milestones", { method: "POST", data }),
  updateMilestone: (id: number, data: MilestoneInput) =>
    apiRequest<Milestone>(`/milestones/${id}`, { method: "PUT", data }),
  deleteMilestone: (id: number) => apiRequest<void>(`/milestones/${id}`, { method: "DELETE" }),
  feedings: (from?: string, to?: string) =>
    apiRequest<PageResult<FeedingRecord>>(`/feedings${query({
      from: rangeDateTime(from),
      to: rangeDateTime(to),
      page: 0,
      size: 100,
    })}`),
  feeding: (id: number) => apiRequest<FeedingRecord>(`/feedings/${id}`),
  createFeeding: (data: FeedingInput) => apiRequest<FeedingRecord>("/feedings", { method: "POST", data }),
  updateFeeding: (id: number, data: FeedingInput) =>
    apiRequest<FeedingRecord>(`/feedings/${id}`, { method: "PUT", data }),
  deleteFeeding: (id: number) => apiRequest<void>(`/feedings/${id}`, { method: "DELETE" }),
  milkStorages: (from?: string, to?: string) =>
    apiRequest<PageResult<MilkStorageRecord>>(`/milk-storage-records${query({
      from: rangeDateTime(from),
      to: rangeDateTime(to),
      page: 0,
      size: 100,
    })}`),
  milkStorage: (id: number) => apiRequest<MilkStorageRecord>(`/milk-storage-records/${id}`),
  createMilkStorage: (data: MilkStorageInput) =>
    apiRequest<MilkStorageRecord>("/milk-storage-records", { method: "POST", data }),
  updateMilkStorage: (id: number, data: MilkStorageInput) =>
    apiRequest<MilkStorageRecord>(`/milk-storage-records/${id}`, { method: "PUT", data }),
  deleteMilkStorage: (id: number) => apiRequest<void>(`/milk-storage-records/${id}`, { method: "DELETE" }),
  diapers: (from?: string, to?: string) =>
    apiRequest<PageResult<DiaperRecord>>(`/diapers${query({ from, to, page: 0, size: 100 })}`),
  diaper: (id: number) => apiRequest<DiaperRecord>(`/diapers/${id}`),
  createDiaper: (data: DiaperInput) => apiRequest<DiaperRecord>("/diapers", { method: "POST", data }),
  updateDiaper: (id: number, data: DiaperInput) =>
    apiRequest<DiaperRecord>(`/diapers/${id}`, { method: "PUT", data }),
  deleteDiaper: (id: number) => apiRequest<void>(`/diapers/${id}`, { method: "DELETE" }),
  weightRecords: (from?: string, to?: string) =>
    apiRequest<PageResult<WeightRecord>>(`/weight-records${query({ from, to, page: 0, size: 100 })}`),
  weightRecord: (id: number) => apiRequest<WeightRecord>(`/weight-records/${id}`),
  createWeight: (data: WeightInput) => apiRequest<WeightRecord>("/weight-records", { method: "POST", data }),
  updateWeight: (id: number, data: WeightInput) =>
    apiRequest<WeightRecord>(`/weight-records/${id}`, { method: "PUT", data }),
  deleteWeight: (id: number) => apiRequest<void>(`/weight-records/${id}`, { method: "DELETE" }),
  events: (from?: string, to?: string) =>
    apiRequest<PageResult<EventRecord>>(`/events${query({ from, to, page: 0, size: 100 })}`),
  event: (id: number) => apiRequest<EventRecord>(`/events/${id}`),
  createEvent: (data: EventInput) => apiRequest<EventRecord>("/events", { method: "POST", data }),
  updateEvent: (id: number, data: EventInput) =>
    apiRequest<EventRecord>(`/events/${id}`, { method: "PUT", data }),
  deleteEvent: (id: number) => apiRequest<void>(`/events/${id}`, { method: "DELETE" }),
  tasks: (status?: TaskStatus) => apiRequest<TodoTask[]>(`/tasks${query({ status })}`),
  task: (id: number) => apiRequest<TodoTask>(`/tasks/${id}`),
  dueReminders: () => apiRequest<TodoTask[]>("/tasks/due-reminders"),
  createTask: (data: TaskInput) => apiRequest<TodoTask>("/tasks", { method: "POST", data }),
  updateTask: (id: number, data: TaskInput) => apiRequest<TodoTask>(`/tasks/${id}`, { method: "PUT", data }),
  updateTaskStatus: (id: number, status: TaskStatus) =>
    apiRequest<TodoTask>(`/tasks/${id}/status`, { method: "PATCH", data: { status } }),
  deleteTask: (id: number) => apiRequest<void>(`/tasks/${id}`, { method: "DELETE" }),
  settings: () => apiRequest<AppSetting>("/settings"),
  saveSettings: (data: Omit<AppSetting, "feedingIntervalAnchor">) =>
    apiRequest<AppSetting>("/settings", { method: "PUT", data }),
};
