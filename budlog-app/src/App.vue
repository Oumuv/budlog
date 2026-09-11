<script setup lang="ts">
import { onHide, onLaunch, onShow } from "@dcloudio/uni-app";
import { initializePwa } from "./utils/pwa";
import { startReminderLoop, stopReminderLoop } from "./utils/reminders";

onLaunch(() => initializePwa());
onShow(() => startReminderLoop());
onHide(() => stopReminderLoop());
</script>

<style lang="scss">
@use "./design/tokens.scss";
@use "./design/effects.scss";

page,
body {
  min-height: 100%;
  background: var(--bud-color-canvas);
  color: var(--bud-color-ink);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 15px;
  letter-spacing: 0;
}

view,
text,
button,
input,
textarea {
  box-sizing: border-box;
  letter-spacing: 0;
}

button::after {
  border: 0;
}

.page-shell {
  min-height: 100vh;
  padding: 20px 16px calc(96px + env(safe-area-inset-bottom));
}

.page-shell--form {
  padding-bottom: calc(32px + env(safe-area-inset-bottom));
}

.page-title {
  margin: 0;
  font-size: 25px;
  line-height: 34px;
  font-weight: 760;
}

.page-subtitle {
  display: block;
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 13px;
  line-height: 20px;
}

.section {
  margin-top: 26px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  margin-bottom: 11px;
}

.section-title__text {
  font-size: 16px;
  line-height: 24px;
  font-weight: 720;
}

.surface {
  background: var(--bud-color-surface);
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  box-shadow: var(--bud-shadow-sm);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  margin: 0;
  padding: 0 18px;
  border: 0;
  border-radius: 7px;
  font-size: 15px;
  font-weight: 650;
  line-height: 20px;
}

.btn--primary {
  color: #ffffff;
  background: var(--bud-color-primary);
}

.btn--secondary {
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-primary-soft);
}

.btn--danger {
  color: #a14440;
  background: var(--bud-color-coral-soft);
}

.btn--ghost {
  min-height: 38px;
  padding: 0 10px;
  color: var(--bud-color-primary-dark);
  background: transparent;
}

.btn[disabled] {
  opacity: 0.55;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 50%;
  color: var(--bud-color-body);
  background: transparent;
  transition: background-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.icon-btn:active,
.btn:active {
  opacity: 0.86;
  transform: scale(0.95);
}

.field {
  margin-bottom: 20px;
}

.field__label {
  display: block;
  margin-bottom: 7px;
  color: var(--bud-color-body);
  font-size: 14px;
  font-weight: 650;
}

.field__control {
  width: 100%;
  min-height: 46px;
  padding: 11px 12px;
  border: 1px solid var(--bud-color-line);
  border-radius: 10px;
  color: var(--bud-color-ink);
  background: var(--bud-color-surface);
  font-size: 16px;
}

textarea.field__control {
  min-height: 92px;
  line-height: 23px;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.form-actions .btn {
  flex: 1;
}

.segmented {
  display: grid;
  grid-auto-columns: 1fr;
  grid-auto-flow: column;
  min-height: 44px;
  padding: 3px;
  border: 1px solid var(--bud-color-line);
  border-radius: 10px;
  background: #f6eeee;
}

.segmented__item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 8px;
  border-radius: 8px;
  color: var(--bud-color-muted);
  font-size: 14px;
  font-weight: 650;
}

.segmented__item--active {
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-surface);
  box-shadow: 0 1px 2px rgba(24, 32, 29, 0.08);
}

.muted {
  color: var(--bud-color-muted);
}

.danger-text {
  color: #a14440;
}

.empty-state {
  padding: 34px 20px;
  color: var(--bud-color-muted);
  text-align: center;
}

.state-panel {
  display: flex;
  min-height: 164px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 30px 20px;
  text-align: center;
}

.state-panel__icon {
  display: flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--bud-color-primary);
  background: var(--bud-color-primary-soft);
}

.state-panel__title {
  color: var(--bud-color-ink);
  font-size: 16px;
  font-weight: 700;
}

.state-panel__copy {
  max-width: 300px;
  color: var(--bud-color-muted);
  font-size: 13px;
  line-height: 20px;
}

.form-control {
  width: 100%;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.form-actions > * {
  min-width: 0;
  flex: 1;
}

.n-button .lucide {
  margin-right: 6px;
}

.shortcut-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.shortcut-chip {
  display: inline-flex;
  min-height: 34px;
  margin: 0;
  padding: 0 12px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--bud-color-line);
  border-radius: 17px;
  color: var(--bud-color-body);
  background: var(--bud-color-surface);
  font-size: 13px;
  font-weight: 650;
  line-height: 18px;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.shortcut-chip:active {
  transform: scale(0.96);
}

.shortcut-chip--active {
  border-color: rgba(255, 93, 143, 0.42);
  color: var(--bud-color-primary-dark);
  background: var(--bud-color-primary-soft);
}

.field__hint {
  display: block;
  margin-top: 7px;
  color: var(--bud-color-muted);
  font-size: 12px;
  line-height: 18px;
}

@media (min-width: 760px) {
  .page-shell {
    width: 740px;
    margin: 0 auto;
    padding-right: 26px;
    padding-left: 26px;
  }
}
</style>
