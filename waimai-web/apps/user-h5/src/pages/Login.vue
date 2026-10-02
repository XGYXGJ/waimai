<template>
  <div class="login-page">
    <div class="logo">
      <div class="logo-icon">饿</div>
      <h1>小饿外卖</h1>
      <p>多端智能外卖点餐平台</p>
    </div>

    <van-form @submit="onLogin">
      <van-cell-group inset>
        <van-field v-model="phone" name="phone" label="手机号" placeholder="请输入手机号" type="tel" maxlength="11" :rules="[{ required: true, message: '请输入手机号' }]" />
        <van-field v-model="password" name="password" label="密码" placeholder="请输入密码" type="password" :rules="[{ required: true, message: '请输入密码' }]" />
      </van-cell-group>
      <div style="margin: 24px 16px">
        <van-button round block type="primary" native-type="submit" color="#ff6034">登录</van-button>
      </div>
    </van-form>

    <div class="toggle" @click="openRegister">
      没有账号？去注册
    </div>

    <van-dialog v-model:show="showRegister" title="注册账号" show-cancel-button @confirm="onRegister" :before-close="() => true">
      <van-form style="padding: 16px">
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
import { setToken } from '@waimai/shared';
import { apiLogin, apiRegister } from '@/api';

const router = useRouter();
const route = useRoute();
const phone = ref('');
const password = ref('');
const showRegister = ref(false);

const regPhone = ref('');
const regPassword = ref('');
const regNickname = ref('');

async function onLogin() {
  try {
    const res = await apiLogin(phone.value, password.value);
    setToken(res.accessToken);
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
  background: #fff;
}
.logo {
  text-align: center;
  padding: 60px 0 40px;
}
.logo-icon {
  width: 72px;
  height: 72px;
  line-height: 72px;
  border-radius: 20px;
  background: #ff6034;
  color: #fff;
  font-size: 40px;
  font-weight: bold;
  margin: 0 auto 16px;
}
.logo h1 {
  font-size: 26px;
  color: #333;
}
.logo p {
  color: #999;
  font-size: 14px;
  margin-top: 8px;
}
.toggle {
  text-align: center;
  color: #ff6034;
  margin-top: 16px;
  font-size: 14px;
}
</style>
