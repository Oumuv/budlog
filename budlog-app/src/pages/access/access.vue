<script setup lang="ts">
import { Baby, Eye, EyeOff, LockKeyhole } from "lucide-vue-next";
import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import { verifyPassword } from "../../api/request";
import { getPassword, setPassword } from "../../utils/auth";
import { switchValue } from "../../utils/events";
import { resetAccessVerification } from "../../utils/guard";

const password = ref("");
const remember = ref(true);
const visible = ref(false);
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
    uni.reLaunch({ url: "/pages/home/home" });
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
    uni.reLaunch({ url: "/pages/home/home" });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "验证失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <view class="access-page">
    <view class="access-brand">
      <view class="access-brand__mark"><Baby :size="28" /></view>
      <text class="access-brand__name">Budlog</text>
      <text class="access-brand__caption">家庭育儿日记</text>
    </view>

    <view class="access-form">
      <text class="access-form__title">家庭访问密码</text>
      <view class="password-field">
        <LockKeyhole class="password-field__leading" :size="20" />
        <input
          v-model="password"
          class="password-field__input"
          :password="!visible"
          placeholder="请输入密码"
          confirm-type="done"
          @confirm="submit"
        />
        <button class="password-field__toggle" :aria-label="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible">
          <EyeOff v-if="visible" :size="20" />
          <Eye v-else :size="20" />
        </button>
      </view>
      <view class="remember-row">
        <switch :checked="remember" color="#216454" style="transform: scale(0.75)" @change="remember = switchValue($event)" />
        <text>在此设备记住密码</text>
      </view>
      <text v-if="error" class="access-error">{{ error }}</text>
      <button class="btn btn--primary access-submit" :disabled="!password || loading" @click="submit">
        {{ loading ? "验证中" : "进入 Budlog" }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.access-page {
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: min(100%, 440px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 28px 24px;
  background: #ffffff;
}

.access-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 44px;
}

.access-brand__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  border-radius: 8px;
  color: #ffffff;
  background: #216454;
}

.access-brand__name {
  font-size: 30px;
  line-height: 38px;
  font-weight: 800;
}

.access-brand__caption {
  margin-top: 4px;
  color: #6f7b76;
  font-size: 14px;
}

.access-form__title {
  display: block;
  margin-bottom: 12px;
  font-size: 17px;
  font-weight: 700;
}

.password-field {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 44px;
  align-items: center;
  height: 50px;
  border: 1px solid #bdc9c3;
  border-radius: 7px;
}

.password-field:focus-within {
  border-color: #216454;
  box-shadow: 0 0 0 3px rgba(33, 100, 84, 0.12);
}

.password-field__leading {
  justify-self: end;
  color: #6b7872;
}

.password-field__input {
  width: 100%;
  height: 48px;
  padding: 0 10px;
  font-size: 16px;
}

.password-field__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 48px;
  margin: 0;
  padding: 0;
  color: #61706a;
  background: transparent;
}

.remember-row {
  display: flex;
  align-items: center;
  min-height: 44px;
  margin: 4px 0 12px -7px;
  color: #53615c;
  font-size: 13px;
}

.access-error {
  display: block;
  margin-bottom: 12px;
  color: #a43835;
  font-size: 13px;
}

.access-submit {
  width: 100%;
}

@media (min-width: 600px) {
  .access-page {
    min-height: 620px;
    margin-top: 5vh;
    border: 1px solid #dfe5e1;
    border-radius: 8px;
  }
}
</style>
