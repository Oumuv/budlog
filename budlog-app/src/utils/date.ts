const pad = (value: number) => String(value).padStart(2, "0");
const TIMEZONE_KEY = "budlog.timezone";
const DEFAULT_TIMEZONE = "Asia/Shanghai";

interface ZonedParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function validateTimezone(timezone: string): string {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: timezone }).format();
    return timezone;
  } catch {
    throw new Error(`不支持的时区：${timezone}`);
  }
}

export function getAppTimezone(): string {
  const stored = typeof uni === "undefined" ? "" : String(uni.getStorageSync(TIMEZONE_KEY) || "");
  try {
    return validateTimezone(stored || DEFAULT_TIMEZONE);
  } catch {
    return DEFAULT_TIMEZONE;
  }
}

export function setAppTimezone(timezone: string): void {
  const resolved = validateTimezone(timezone);
  if (typeof uni !== "undefined") uni.setStorageSync(TIMEZONE_KEY, resolved);
}

function zonedParts(date: Date, timezone: string): ZonedParts {
  if (Number.isNaN(date.getTime())) throw new Error("时间格式不正确");
  const values: Record<string, string> = {};
  new Intl.DateTimeFormat("en-CA", {
    timeZone: validateTimezone(timezone),
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date).forEach((part) => {
    values[part.type] = part.value;
  });
  return {
    year: Number(values.year),
    month: Number(values.month),
    day: Number(values.day),
    hour: Number(values.hour) % 24,
    minute: Number(values.minute),
    second: Number(values.second),
  };
}

function timezoneOffset(date: Date, timezone: string): number {
  const parts = zonedParts(date, timezone);
  const zonedAsUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
  const instantAtSecond = Math.floor(date.getTime() / 1000) * 1000;
  return zonedAsUtc - instantAtSecond;
}

function formatLocal(parts: ZonedParts): string {
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}T${pad(parts.hour)}:${pad(parts.minute)}`;
}

export function nowLocalInput(timezone = getAppTimezone()): string {
  return toLocalInput(new Date().toISOString(), timezone);
}

export function toLocalInput(value?: string, timezone = getAppTimezone()): string {
  if (!value) return "";
  return formatLocal(zonedParts(new Date(value), timezone));
}

export function toIso(value: string, timezone = getAppTimezone()): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) throw new Error("时间格式不正确");
  const [, yearText, monthText, dayText, hourText, minuteText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const localAsUtc = Date.UTC(year, month - 1, day, hour, minute);
  const normalized = new Date(localAsUtc);
  if (
    normalized.getUTCFullYear() !== year || normalized.getUTCMonth() !== month - 1
    || normalized.getUTCDate() !== day || normalized.getUTCHours() !== hour
    || normalized.getUTCMinutes() !== minute
  ) {
    throw new Error("时间格式不正确");
  }

  let instant = localAsUtc;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const adjusted = localAsUtc - timezoneOffset(new Date(instant), timezone);
    if (adjusted === instant) break;
    instant = adjusted;
  }
  const result = new Date(instant);
  if (toLocalInput(result.toISOString(), timezone) !== value) {
    throw new Error("所选时间在当前时区不存在，请调整时间");
  }
  return result.toISOString();
}

export function todayKey(date = new Date(), timezone = getAppTimezone()): string {
  const parts = zonedParts(date, timezone);
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}`;
}

export function shiftDay(value: string, amount: number): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + amount);
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export function formatDateTime(value?: string, timezone = getAppTimezone()): string {
  if (!value) return "--";
  const parts = zonedParts(new Date(value), timezone);
  return `${pad(parts.month)}-${pad(parts.day)} ${pad(parts.hour)}:${pad(parts.minute)}`;
}

export function formatDate(value?: string, timezone = getAppTimezone()): string {
  if (!value) return "--";
  const parts = zonedParts(new Date(value), timezone);
  return `${pad(parts.month)}-${pad(parts.day)}`;
}

export function formatTime(value?: string, timezone = getAppTimezone()): string {
  if (!value) return "--:--";
  const parts = zonedParts(new Date(value), timezone);
  return `${pad(parts.hour)}:${pad(parts.minute)}`;
}

export function formatDuration(minutes?: number): string {
  if (minutes === undefined || minutes === null) return "暂无";
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}

export function uuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16);
    const value = char === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}
