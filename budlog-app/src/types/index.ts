export type FeedingType = "BREAST_DIRECT" | "BREAST_BOTTLE" | "FORMULA_BOTTLE";
export type BreastSide = "LEFT" | "RIGHT" | "BOTH";
export type DiaperType = "PEE" | "POOP" | "BOTH";
export type TaskStatus = "TODO" | "DONE" | "CANCELED";

export interface Baby {
  id: number;
  name: string;
  birthTime: string;
  timezone: string;
  note?: string;
  ageDayNumber: number;
  ageDurationDays: number;
  ageDurationHours: number;
  ageMonths: number;
  ageRemainingDays: number;
}

export interface BabyInput {
  name: string;
  birthTime: string;
  timezone: string;
  note?: string;
}

export interface Milestone {
  id?: number;
  code: string;
  system: boolean;
  title: string;
  targetTime: string;
  daysDifference: number;
  note?: string;
}

export interface MilestoneInput {
  clientRequestId: string;
  title: string;
  targetTime: string;
  note?: string;
}

export interface FeedingRecord {
  id: number;
  clientRequestId: string;
  feedingType: FeedingType;
  breastSide?: BreastSide;
  startTime: string;
  endTime?: string;
  amountMl?: number;
  note?: string;
  durationMinutes?: number;
  minutesSincePrevious?: number;
  nextExpectedTime: string;
}

export interface FeedingInput {
  clientRequestId: string;
  feedingType: FeedingType;
  breastSide?: BreastSide;
  startTime: string;
  endTime?: string;
  amountMl?: number;
  note?: string;
}

export interface MilkStorageRecord {
  id: number;
  clientRequestId: string;
  storedAt: string;
  amountMl: number;
  note?: string;
}

export interface MilkStorageInput {
  clientRequestId: string;
  storedAt: string;
  amountMl: number;
  note?: string;
}

export interface DiaperRecord {
  id: number;
  clientRequestId: string;
  recordType: DiaperType;
  recordTime: string;
  note?: string;
}

export interface DiaperInput {
  clientRequestId: string;
  recordType: DiaperType;
  recordTime: string;
  note?: string;
}

export interface TodoTask {
  id: number;
  clientRequestId: string;
  title: string;
  description?: string;
  dueTime: string;
  remindTime?: string;
  status: TaskStatus;
  completedAt?: string;
  overdue: boolean;
  reminderDue: boolean;
}

export interface TaskInput {
  clientRequestId: string;
  title: string;
  description?: string;
  dueTime: string;
  remindTime?: string;
}

export interface AppSetting {
  defaultFeedingIntervalMin: number;
  feedingIntervalAnchor: "START";
  reminderSoundEnabled: boolean;
  reminderVibrateEnabled: boolean;
}

export interface DailySummary {
  feedingCount: number;
  bottleAmountMl: number;
  directFeedingMinutes: number;
  milkStorageCount: number;
  storedMilkAmountMl: number;
  peeCount: number;
  poopCount: number;
}

export interface TimelineItem {
  id: number;
  category: "FEEDING" | "DIAPER" | "MILK_STORAGE" | "TASK";
  recordType: string;
  eventTime: string;
  title: string;
  subtitle?: string;
}

export interface Timeline {
  date: string;
  summary: DailySummary;
  items: TimelineItem[];
}

export interface Dashboard {
  configured: boolean;
  date?: string;
  baby?: Baby;
  milestones: Milestone[];
  nextMilestone?: Milestone;
  lastFeeding?: FeedingRecord;
  lastFeedingMinutesAgo?: number;
  nextExpectedFeedingTime?: string;
  nextFeedingMinutesRemaining?: number;
  lastPee?: DiaperRecord;
  lastPeeMinutesAgo?: number;
  lastPoop?: DiaperRecord;
  lastPoopMinutesAgo?: number;
  todaySummary: DailySummary;
  tasks: TodoTask[];
  recentTimeline: TimelineItem[];
  settings: AppSetting;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
