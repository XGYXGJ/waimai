<template>
  <div class="login-page">
    <!-- 品牌区：logo + 标语 -->
    <section class="card brand">
      <div class="brand-logo" aria-hidden="true">饿</div>
      <h1 class="brand-name">小饿外卖</h1>
      <p class="brand-desc">多端智能外卖点餐平台</p>
    </section>

    <!-- 登录表单 -->
    <van-form class="card login-form" @submit="onLogin">
      <van-cell-group inset>
        <van-field v-model="phone" name="phone" label="手机号" placeholder="请输入手机号" type="tel" maxlength="11" :rules="[{ required: true, message: '请输入手机号' }]" />
        <van-field v-model="password" name="password" label="密码" placeholder="请输入密码" type="password" :rules="[{ required: true, message: '请输入密码' }]" />
      </van-cell-group>
      <!-- 「30 天免登录」：勾选后后端签发 30 天有效期的 refresh token（硬过期，刷新不延长） -->
      <div class="remember-row">
        <van-checkbox v-model="remember" shape="square" checked-color="var(--wm-primary)" icon-size="16px">
          <span class="remember-text">30 天免登录</span>
        </van-checkbox>
      </div>
      <van-button class="submit-btn" round block type="primary" native-type="submit" color="var(--wm-primary)">登录</van-button>
      <div class="toggle" @click="openRegister">
        没有账号？去注册
      </div>
    </van-form>

    <van-dialog v-model:show="showRegister" title="注册账号" show-cancel-button @confirm="onRegister" :before-close="() => true">
      <van-form class="reg-form">
        <van-field v-model="regPhone" label="手机号" placeholder="手机号" type="tel" maxlength="11" :rules="[{ required: true, message: '请输入手机号' }]" />
        <van-field v-model="regPassword" label="密码" placeholder="密码（至少6位）" type="password" :rules="[{ required: true, message: '请输入密码' }]" />
        <van-field v-model="regNickname" label="昵称" placeholder="昵称（选填）" />
      </van-form>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { showToast } from 'vant';
import { setAuth } from '@waimai/shared';
import { apiLogin, apiRegister } from '@/api';

const router = useRouter();
const route = useRoute();
const phone = ref('');
const password = ref('');
const showRegister = ref(false);
/** 勾选后 30 天内不用重复登录（默认勾选：C 端体验优先） */
const remember = ref(true);

const regPhone = ref('');
const regPassword = ref('');
const regNickname = ref('');

async function onLogin() {
  try {
    const res = await apiLogin(phone.value, password.value, remember.value);
    setAuth(res.accessToken, res.refreshToken);
    showToast('登录成功');
    router.replace((route.query.redirect as string) || '/');
  } catch (e: any) {
    showToast(e.message);
  }
}

function openRegister() {
  regPhone.value = phone.value;
  regPassword.value = '';
  regNickname.value = '';
  showRegister.value = true;
}

async function onRegister() {
  if (!regPhone.value || !regPassword.value) {
    showToast('请填写手机号和密码');
    return;
  }
  if (regPassword.value.length < 6) {
    showToast('密码至少6位');
    return;
  }
  try {
    await apiRegister({ phone: regPhone.value, password: regPassword.value, nickname: regNickname.value, role: 'USER' });
    showToast('注册成功，请登录');
    phone.value = regPhone.value;
    password.value = regPassword.value;
  } catch (e: any) {
    showToast(e.message);
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  /* 底部安全区，避免小屏机型遮挡 */
  padding-bottom: calc(var(--wm-space-6) + env(safe-area-inset-bottom));
}

/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 品牌区 ---------------- */
.brand {
  padding: var(--wm-space-8) var(--wm-space-4);
  text-align: center;
}

.brand-logo {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  margin: 0 auto var(--wm-space-3);
  border-radius: var(--wm-radius-xl);
  background: var(--wm-primary);
  color: #fff;
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  box-shadow: var(--wm-shadow-primary);
}

.brand-name {
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.brand-desc {
  margin-top: var(--wm-space-2);
  font-size: var(--wm-font-md);
  color: var(--wm-text-3);
}

/* ---------------- 表单 ---------------- */
/* 输入行本身就是卡片内容：去掉 Vant 内层缩进与圆角，避免卡片套卡片 */
.login-form :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: 0;
  overflow: visible;
}

/* 输入控件可点高度不低于 44px */
.login-form :deep(.van-field),
.reg-form :deep(.van-field) {
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  background: transparent;
}

/* 标签用次要色，输入值用主文字色，层级拉开 */
.login-form :deep(.van-field__label),
.reg-form :deep(.van-field__label) {
  color: var(--wm-text-3);
  font-size: var(--wm-font-md);
}

.login-form :deep(.van-field__control),
.reg-form :deep(.van-field__control) {
  color: var(--wm-text-1);
  font-size: var(--wm-font-md);
}

.login-form :deep(.van-field__control)::placeholder,
.reg-form :deep(.van-field__control)::placeholder {
  color: var(--wm-text-4);
}

/* 校验错误提示统一用语义危险色 */
.login-form :deep(.van-field__error-message),
.reg-form :deep(.van-field__error-message) {
  color: var(--wm-danger);
  font-size: var(--wm-font-sm);
}

.login-form :deep(.van-field::after),
.reg-form :deep(.van-field::after) {
  left: 0;
  right: 0;
  border-bottom-color: var(--wm-border);
}

.login-form :deep(.van-field:last-child::after),
.reg-form :deep(.van-field:last-child::after) {
  display: none;
}

.submit-btn {
  min-height: var(--wm-tap-min);
  margin-top: var(--wm-space-4);
  font-size: var(--wm-font-lg);
  font-weight: 600;
}

.toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: var(--wm-tap-min);
  margin-top: var(--wm-space-3);
  color: var(--wm-primary);
  font-size: var(--wm-font-md);
  cursor: pointer;
}

.reg-form {
  padding: var(--wm-space-4);
}

/* ---------------- 30 天免登录 ---------------- */
.remember-row {
  display: flex;
  align-items: center;
  min-height: var(--wm-tap-min);
  padding-left: var(--wm-space-1);
}

.remember-text {
  color: var(--wm-text-2);
  font-size: var(--wm-font-md);
}
</style>
