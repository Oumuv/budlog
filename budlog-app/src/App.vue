<script setup lang="ts">
import { onHide, onLaunch, onShow } from "@dcloudio/uni-app";
import { startReminderLoop, stopReminderLoop } from "./utils/reminders";

onLaunch(() => undefined);
onShow(() => startReminderLoop());
onHide(() => stopReminderLoop());
</script>

<style lang="scss">
page {
  --bud-color-ink: #18211e;
  --bud-color-body: #40504a;
  --bud-color-muted: #68756f;
  --bud-color-line: #dfe6e2;
  --bud-color-line-soft: #edf1ef;
  --bud-color-canvas: #f4f7f5;
  --bud-color-surface: #ffffff;
  --bud-color-primary: #216b59;
  --bud-color-primary-dark: #174c40;
  --bud-color-primary-soft: #e1f0ea;
  --bud-color-coral: #ba564d;
  --bud-color-coral-soft: #fae9e5;
  --bud-color-cyan: #176e78;
  --bud-color-cyan-soft: #e0f0f1;
  --bud-color-gold: #9b721c;
  --bud-color-gold-soft: #f7efce;
  --bud-shadow-sm: 0 2px 10px rgba(24, 33, 30, 0.05);
  --bud-shadow-md: 0 8px 24px rgba(24, 33, 30, 0.08);
  --wot-color-theme: var(--bud-color-primary);
  --wot-color-success: #2f8069;
  --wot-color-warning: #b47c27;
  --wot-color-danger: var(--bud-color-coral);
  --wot-color-title: var(--bud-color-ink);
  --wot-color-content: var(--bud-color-body);
  --wot-color-secondary: #62716b;
  --wot-color-aid: var(--bud-color-muted);
  --wot-color-tip: #9ca6a2;
  --wot-color-border: #cdd8d2;
  --wot-color-border-light: var(--bud-color-line-soft);
  --wot-color-bg: var(--bud-color-canvas);
  --wot-button-medium-height: 40px;
  --wot-button-medium-radius: 6px;
  --wot-button-large-height: 48px;
  --wot-button-large-radius: 8px;
  --wot-input-inner-height: 46px;
  --wot-input-padding: 12px 13px;
  --wot-input-bg: var(--bud-color-surface);
  --wot-textarea-bg: var(--bud-color-surface);
  --wot-segmented-radius: 7px;
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
  border-radius: 7px;
  color: #355149;
  background: transparent;
}

.icon-btn:active,
.btn:active {
  opacity: 0.78;
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
  border: 1px solid #cdd8d2;
  border-radius: 7px;
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
  border: 1px solid #d4ddd8;
  border-radius: 7px;
  background: #e9efec;
}

.segmented__item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 8px;
  border-radius: 5px;
  color: #56635e;
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
  border-radius: 8px;
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
  border: 1px solid #cdd8d2;
  border-radius: 7px;
  background: var(--bud-color-surface);
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

@media (min-width: 760px) {
  .page-shell {
    width: 740px;
    margin: 0 auto;
    padding-right: 26px;
    padding-left: 26px;
  }
}
</style>
