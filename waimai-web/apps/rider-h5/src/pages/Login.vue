<template>
  <div class="login-wrap">
    <div class="logo">🛵 骑手端</div>
    <van-form @submit="login">
      <van-cell-group inset>
        <van-field v-model="phone" label="手机号" placeholder="请输入手机号" type="tel" />
        <van-field v-model="password" label="密码" placeholder="请输入密码" type="password" />
      </van-cell-group>
      <div style="margin: 24px 16px">
        <van-button round block type="primary" native-type="submit" color="#07c160">登录</van-button>
      </div>
    </van-form>
    <div class="toggle" @click="openRegister">还没有骑手账号？去注册</div>

    <van-dialog v-model:show="showRegister" title="骑手注册" show-cancel-button @confirm="register">
      <van-form style="padding: 16px">
        <van-field v-model="regPhone" label="手机号" placeholder="手机号" type="tel" maxlength="11" />
        <van-field v-model="regPassword" label="密码" placeholder="密码（至少6位）" type="password" />
        <van-field v-model="regNickname" label="姓名" placeholder="真实姓名（选填）" />
      </van-form>
      <div style="padding: 0 16px 12px; font-size: 12px; color: #999">提交后需等待管理员审核，审核通过后方可接单。</div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { setToken } from '@waimai/shared';
import { apiLogin, apiRegister } from '@/api';

const router = useRouter();
const phone = ref('');
const password = ref('');
const showRegister = ref(false);

const regPhone = ref('');
const regPassword = ref('');
const regNickname = ref('');

async function login() {
  try {
    const res = await apiLogin(phone.value, password.value);
    setToken(res.accessToken);
    showToast('登录成功');
    router.replace('/');
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

async function register() {
  if (!regPhone.value) {
    showToast('请输入手机号');
    return;
  }
  if (!regPassword.value || regPassword.value.length < 6) {
    showToast('密码至少6位');
    return;
  }
  try {
    await apiRegister({ phone: regPhone.value, password: regPassword.value, nickname: regNickname.value, role: 'RIDER' });
    showToast('注册申请已提交，请等待审核');
    phone.value = regPhone.value;
    password.value = regPassword.value;
  } catch (e: any) {
    showToast(e.message);
  }
}
</script>

<style scoped>
.login-wrap {
  padding-top: 80px;
}
.logo {
  text-align: center;
  font-size: 28px;
  margin-bottom: 40px;
}
.toggle {
  text-align: center;
  color: #07c160;
  margin-top: 16px;
  font-size: 14px;
}
</style>
