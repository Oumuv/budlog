import { api } from "../api";
import type { AppSetting } from "../types";
import { getPassword } from "./auth";
import { formatDateTime } from "./date";

const SHOWN_KEY = "budlog.reminders.shown";
let timer: ReturnType<typeof setInterval> | undefined;
let checking = false;

function playBeep(): void {
  // #ifdef H5
  const WindowWithAudio = window as unknown as {
    AudioContext?: typeof AudioContext;
    webkitAudioContext?: typeof AudioContext;
  };
  const AudioContextCtor = WindowWithAudio.AudioContext || WindowWithAudio.webkitAudioContext;
  if (!AudioContextCtor) return;
  const context = new AudioContextCtor();
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.frequency.value = 720;
  gain.gain.value = 0.05;
  oscillator.connect(gain);
  gain.connect(context.destination);
  oscillator.start();
  oscillator.stop(context.currentTime + 0.18);
  // #endif
}

async function notify(settings: AppSetting): Promise<void> {
  if (checking || !getPassword()) return;
  checking = true;
  try {
    const tasks = await api.dueReminders();
    const shown = new Set<number>((uni.getStorageSync(SHOWN_KEY) || []) as number[]);
    const task = tasks.find((item) => !shown.has(item.id));
    if (!task) return;
    shown.add(task.id);
    uni.setStorageSync(SHOWN_KEY, Array.from(shown));
    if (settings.reminderVibrateEnabled) uni.vibrateLong({});
    if (settings.reminderSoundEnabled) playBeep();
    uni.showModal({
      title: task.title,
      content: `到期时间 ${formatDateTime(task.dueTime)}`,
      confirmText: "完成",
      cancelText: "知道了",
      success: (result) => {
        if (result.confirm) {
          api.updateTaskStatus(task.id, "DONE")
            .then(() => clearShownReminder(task.id))
            .catch(() => clearShownReminder(task.id));
        }
      },
    });
  } catch {
    // Reminder failures must not interrupt the form currently being edited.
  } finally {
    checking = false;
  }
}

async function check(): Promise<void> {
  if (!getPassword()) return;
  try {
    await notify(await api.settings());
  } catch {
    // Regular page requests surface connectivity and authentication failures.
  }
}

export function startReminderLoop(): void {
  stopReminderLoop();
  check();
  timer = setInterval(check, 60_000);
}

export function stopReminderLoop(): void {
  if (timer) clearInterval(timer);
  timer = undefined;
}

export function clearShownReminder(taskId: number): void {
  const shown = new Set<number>((uni.getStorageSync(SHOWN_KEY) || []) as number[]);
  shown.delete(taskId);
  uni.setStorageSync(SHOWN_KEY, Array.from(shown));
}
