<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>管理后台登录</h2>
      <el-form @submit.prevent="login">
        <el-form-item>
          <el-input v-model="phone" placeholder="管理员手机号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" placeholder="密码" type="password" @keyup.enter="login" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="remember" class="remember-row">30 天免登录</el-checkbox>
        </el-form-item>
        <el-button type="primary" style="width: 100%" @click="login">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { setAuth } from '@waimai/shared';
import { apiLogin } from '@/api';

const router = useRouter();
const phone = ref('');
const password = ref('');
const remember = ref(false);

async function login() {
  try {
    const res = await apiLogin(phone.value, password.value, remember.value);
    setAuth(res.accessToken, res.refreshToken);
    ElMessage.success('登录成功');
    router.replace('/');
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
  background: #0f1e2e;
}
.login-card {
  width: 360px;
}
.remember-row {
  height: 44px;
  display: flex;
  align-items: center;
}
h2 {
  text-align: center;
  margin-bottom: 24px;
}
</style>
