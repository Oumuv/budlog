<script setup lang="ts">
import { onHide, onLaunch, onShow } from "@dcloudio/uni-app";
import { initializePwa } from "./utils/pwa";
import { startReminderLoop, stopReminderLoop } from "./utils/reminders";

onLaunch(() => initializePwa());
onShow(() => startReminderLoop());
onHide(() => stopReminderLoop());
</script>

<style lang="scss">
page,
body {
  --bud-color-ink: #30292b;
  --bud-color-body: #5c4f52;
  --bud-color-muted: #7d6c6f;
  --bud-color-line: #eadcdd;
  --bud-color-line-soft: #f4ebea;
  --bud-color-canvas: #fff8f7;
  --bud-color-surface: #ffffff;
  --bud-color-primary: #b94b5d;
  --bud-color-primary-dark: #873342;
  --bud-color-primary-soft: #fbe7eb;
  --bud-color-coral: #c5655e;
  --bud-color-coral-soft: #fce9e6;
  --bud-color-sage: #497c69;
  --bud-color-sage-soft: #e4f1eb;
  --bud-color-cyan: #39798b;
  --bud-color-cyan-soft: #e3f1f4;
  --bud-color-gold: #9a6c1c;
  --bud-color-gold-soft: #fff1cf;
  --bud-shadow-sm: 0 3px 12px rgba(112, 70, 76, 0.07);
  --bud-shadow-md: 0 12px 32px rgba(112, 70, 76, 0.12);
  --wot-color-theme: var(--bud-color-primary);
  --wot-color-success: var(--bud-color-sage);
  --wot-color-warning: var(--bud-color-gold);
  --wot-color-danger: #b34747;
  --wot-color-title: var(--bud-color-ink);
  --wot-color-content: var(--bud-color-body);
  --wot-color-secondary: #746568;
  --wot-color-aid: var(--bud-color-muted);
  --wot-color-tip: #aa999c;
  --wot-color-border: #dfcecf;
  --wot-color-border-light: var(--bud-color-line-soft);
  --wot-color-bg: var(--bud-color-canvas);
  --wot-button-primary-bg-color: var(--bud-color-primary);
  --wot-calendar-active-color: var(--bud-color-primary);
  --wot-calendar-range-color: var(--bud-color-primary-soft);
  --wot-picker-toolbar-finish-color: var(--bud-color-primary);
  --wot-switch-active-color: var(--bud-color-primary);
  --wot-button-medium-height: 40px;
  --wot-button-medium-radius: 10px;
  --wot-button-large-height: 48px;
  --wot-button-large-radius: 12px;
  --wot-button-info-bg-color: #f7efef;
  --wot-button-info-color: var(--bud-color-body);
  --wot-button-info-plain-border-color: var(--bud-color-line);
  --wot-button-info-plain-normal-color: var(--bud-color-body);
  --wot-input-inner-height: 46px;
  --wot-input-padding: 12px 13px;
  --wot-input-bg: var(--bud-color-surface);
  --wot-textarea-bg: var(--bud-color-surface);
  --wot-segmented-radius: 10px;
  --wot-cell-tap-bg: var(--bud-color-primary-soft);
  --wot-input-number-height: 42px;
  --wot-input-number-btn-width: 44px;
  --wot-input-number-input-width: 76px;
  --wot-input-number-radius: 10px;
  --wot-input-number-fs: 17px;
  --wot-input-number-icon-size: 18px;
  --wot-input-number-icon-color: var(--bud-color-primary-dark);
  --wot-input-number-border-color: var(--bud-color-line);
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

.wd-button {
  font-weight: 700;
  letter-spacing: 0;
}

.wd-button.is-round {
  border-radius: 12px !important;
}

.wd-button.is-primary:not(.is-plain) {
  box-shadow: 0 7px 16px rgba(185, 75, 93, 0.22);
}

.wd-button.is-error.is-plain {
  background: #fffafa;
}

.wd-button__text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
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
  border: 1px solid var(--wot-color-border);
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

.wot-control {
  width: 100%;
}

.wot-control.wd-input,
.wot-control.wd-textarea {
  border: 1px solid var(--wot-color-border);
  border-radius: 10px;
  background: var(--bud-color-surface);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.wot-control.wd-input {
  box-sizing: border-box;
  min-height: 46px;
  padding: 10px 12px;
}

.wot-control.wd-input:focus-within,
.wot-control.wd-textarea:focus-within {
  border-color: rgba(185, 75, 93, 0.72);
  box-shadow: 0 0 0 3px rgba(185, 75, 93, 0.1);
}

.wot-control.wd-input.is-error,
.wot-control.wd-textarea.is-error {
  border-color: var(--bud-color-coral);
}

.wot-segmented {
  width: 100%;
}

.wot-action-row {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.wot-action-row > * {
  min-width: 0;
  flex: 1;
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
  background: #fffdfd;
  font-size: 13px;
  font-weight: 650;
  line-height: 18px;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.shortcut-chip:active {
  transform: scale(0.96);
}

.shortcut-chip--active {
  border-color: rgba(185, 75, 93, 0.38);
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
