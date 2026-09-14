<script setup lang="ts">
import { LockKeyhole, ShieldCheck } from "lucide-vue-next";
import { NButton, NInput, NSwitch } from "naive-ui";
import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import { verifyPassword } from "../../api/request";
import BrandMark from "../../components/BrandMark.vue";
import AppPage from "../../components/AppPage.vue";
import { getPassword, setPassword } from "../../utils/auth";
import { resetAccessVerification } from "../../utils/guard";

const password = ref("");
const remember = ref(true);
const loading = ref(false);
const error = ref("");

onLoad(async () => {
  const saved = getPassword();
  if (saved) {
    password.value = saved;
    await submit();
    return;
  }
  try {
    await verifyPassword("");
    resetAccessVerification();
    uni.reLaunch({ url: "/pages/home/index" });
  } catch {
    // Password-protected environments stay on the access page.
  }
});

async function submit() {
  if (!password.value || loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    await verifyPassword(password.value);
    setPassword(password.value, remember.value);
    resetAccessVerification();
    uni.reLaunch({ url: "/pages/home/index" });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "验证失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <AppPage>
    <view class="access-page">
    <view class="access-card">
      <view class="access-brand">
        <BrandMark size="large" />
        <view>
          <text class="access-brand__name">Budlog</text>
          <text class="access-brand__caption">家庭育儿日记</text>
        </view>
      </view>

      <view class="access-intro">
        <view class="access-intro__icon"><ShieldCheck :size="20" /></view>
        <view>
          <text class="access-intro__title">欢迎回来</text>
          <text class="access-intro__copy">输入家庭访问密码继续</text>
        </view>
      </view>

      <view class="access-form">
        <text class="field__label">家庭访问密码</text>
        <NInput
          v-model:value="password"
          class="access-password"
          clearable
          type="password"
          show-password-on="click"
          placeholder="请输入密码"
          @keyup.enter="submit"
        >
          <template #prefix><LockKeyhole :size="19" /></template>
        </NInput>
        <view class="remember-row">
          <view>
            <text class="remember-row__title">记住密码</text>
            <text class="remember-row__copy">仅保存在当前设备</text>
          </view>
          <NSwitch v-model:value="remember" />
        </view>
        <text v-if="error" class="access-error">{{ error }}</text>
        <NButton
          type="primary"
          size="large"
          block
          :disabled="!password"
          :loading="loading"
          @click="submit"
        >
          {{ loading ? "验证中" : "进入 Budlog" }}
        </NButton>
      </view>
    </view>
    </view>
  </AppPage>
</template>

<style scoped>
.access-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 18px;
  background: var(--bud-color-canvas);
}

.access-card {
  width: min(100%, 430px);
  padding: 28px 24px;
  border: 1px solid var(--bud-color-line);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--bud-shadow-md);
}

.access-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.access-brand__name,
.access-brand__caption {
  display: block;
}

.access-brand__name {
  font-size: 29px;
  line-height: 35px;
  font-weight: 780;
}

.access-brand__caption {
  color: var(--bud-color-muted);
  font-size: 13px;
  line-height: 19px;
}

.access-intro {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 24px 0 20px;
}

.access-intro__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--bud-color-sage);
  background: var(--bud-color-sage-soft);
}

.access-intro__title,
.access-intro__copy {
  display: block;
}

.access-intro__title {
  font-size: 16px;
  font-weight: 700;
}

.access-intro__copy {
  margin-top: 1px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

:deep(.access-password .n-input__prefix) {
  color: var(--bud-color-primary);
}

.remember-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 62px;
  margin: 10px 0 4px;
  border-bottom: 1px solid var(--bud-color-line-soft);
}

.remember-row__title,
.remember-row__copy {
  display: block;
}

.remember-row__title {
  color: var(--bud-color-body);
  font-size: 14px;
  font-weight: 650;
}

.remember-row__copy {
  margin-top: 2px;
  color: var(--bud-color-muted);
  font-size: 12px;
}

.access-error {
  display: block;
  margin: 12px 0;
  padding: 9px 10px;
  border-radius: 6px;
  color: #9f3d42;
  background: var(--bud-color-coral-soft);
  font-size: 13px;
}

@media (max-width: 480px) {
  .access-page {
    align-items: stretch;
    padding: 0;
    background: var(--bud-color-canvas);
  }

  .access-card {
    width: 100%;
    min-height: 100vh;
    padding: 38px 22px;
    border: 0;
    background: var(--bud-color-canvas);
    box-shadow: none;
  }
}
</style>
