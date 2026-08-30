<script setup lang="ts">
import { onHide, onLaunch, onShow } from "@dcloudio/uni-app";
import { startReminderLoop, stopReminderLoop } from "./utils/reminders";

onLaunch(() => undefined);
onShow(() => startReminderLoop());
onHide(() => stopReminderLoop());
</script>

<style lang="scss">
page {
  min-height: 100%;
  background: #f4f6f5;
  color: #18201d;
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
  padding: 18px 16px 100px;
}

.page-shell--form {
  padding-bottom: 32px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  font-weight: 750;
}

.section {
  margin-top: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  margin-bottom: 10px;
}

.section-title__text {
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
}

.surface {
  background: #ffffff;
  border: 1px solid #dfe5e1;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(24, 32, 29, 0.04);
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
  border-radius: 6px;
  font-size: 15px;
  font-weight: 650;
  line-height: 20px;
}

.btn--primary {
  color: #ffffff;
  background: #216454;
}

.btn--secondary {
  color: #183a32;
  background: #e3f0eb;
}

.btn--danger {
  color: #a43835;
  background: #fce9e7;
}

.btn--ghost {
  min-height: 38px;
  padding: 0 10px;
  color: #37524a;
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
  border-radius: 6px;
  color: #37524a;
  background: transparent;
}

.field {
  margin-bottom: 18px;
}

.field__label {
  display: block;
  margin-bottom: 7px;
  color: #40504a;
  font-size: 14px;
  font-weight: 650;
}

.field__control {
  width: 100%;
  min-height: 46px;
  padding: 11px 12px;
  border: 1px solid #cfd8d3;
  border-radius: 6px;
  color: #18201d;
  background: #ffffff;
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
  border: 1px solid #cfd8d3;
  border-radius: 7px;
  background: #edf1ef;
}

.segmented__item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 8px;
  border-radius: 5px;
  color: #53615c;
  font-size: 14px;
  font-weight: 650;
}

.segmented__item--active {
  color: #183a32;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(24, 32, 29, 0.08);
}

.muted {
  color: #73807b;
}

.danger-text {
  color: #a43835;
}

.empty-state {
  padding: 30px 20px;
  color: #73807b;
  text-align: center;
}

@media (min-width: 760px) {
  .page-shell {
    width: 720px;
    margin: 0 auto;
    padding-right: 24px;
    padding-left: 24px;
  }
}
</style>
