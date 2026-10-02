<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>商户登录</h2>
      <el-form @submit.prevent="login">
        <el-form-item>
          <el-input v-model="phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" placeholder="密码" type="password" @keyup.enter="login" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" @click="login">登录</el-button>
      </el-form>
      <div class="reg-link">
        还没有商户账号？
        <el-link type="primary" @click="openRegister">立即入驻</el-link>
      </div>
    </el-card>

    <el-dialog v-model="showRegister" title="商户入驻注册" width="400px">
      <el-form label-width="70px">
        <el-form-item label="手机号">
          <el-input v-model="regPhone" placeholder="手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="regPassword" placeholder="密码（至少6位）" type="password" />
        </el-form-item>
        <el-form-item label="店铺名">
          <el-input v-model="regNickname" placeholder="店铺名称（选填）" />
        </el-form-item>
      </el-form>
      <div class="tip">提交后需等待平台管理员审核，审核通过后方可登录商户端。</div>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" @click="register">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
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
    ElMessage.success('登录成功');
    router.replace('/');
  } catch (e: any) {
    ElMessage.error(e.message);
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
    ElMessage.warning('请输入手机号');
    return;
  }
  if (!regPassword.value || regPassword.value.length < 6) {
    ElMessage.warning('密码至少6位');
    return;
  }
  try {
    await apiRegister({ phone: regPhone.value, password: regPassword.value, nickname: regNickname.value, role: 'MERCHANT' });
    ElMessage.success('入驻申请已提交，请等待管理员审核');
    showRegister.value = false;
    phone.value = regPhone.value;
    password.value = regPassword.value;
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}
.login-card {
  width: 360px;
}
h2 {
  text-align: center;
  margin-bottom: 24px;
}
.reg-link {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}
.tip {
  font-size: 12px;
  color: #999;
  padding: 4px 0 8px;
}
</style>
