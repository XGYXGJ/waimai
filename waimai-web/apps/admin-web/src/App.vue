<template>
  <el-container v-if="isLoggedIn" style="height: 100vh">
    <el-aside width="200px" style="background: #0f1e2e">
      <div class="logo">管理后台</div>
      <el-menu :default-active="$route.path" background-color="#0f1e2e" text-color="#a6adb4" active-text-color="#fff" router>
        <el-menu-item index="/"><el-icon><Odometer /></el-icon>数据大屏</el-menu-item>
        <el-menu-item index="/merchants"><el-icon><Shop /></el-icon>商户管理</el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon>用户管理</el-menu-item>
        <el-menu-item index="/riders"><el-icon><Van /></el-icon>骑手管理</el-menu-item>
        <el-menu-item index="/orders"><el-icon><List /></el-icon>订单管理</el-menu-item>
        <el-menu-item index="/income"><el-icon><Money /></el-icon>收入结算</el-menu-item>
        <el-menu-item index="/reviews"><el-icon><ChatDotRound /></el-icon>评价治理</el-menu-item>
        <el-menu-item index="/bid"><el-icon><TrendCharts /></el-icon>竞价管理</el-menu-item>
        <el-menu-item index="/config"><el-icon><Setting /></el-icon>系统参数</el-menu-item>
        <el-menu-item index="/ai-models"><el-icon><Cpu /></el-icon>AI 模型</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #eee; display: flex; justify-content: flex-end; align-items: center">
        <el-dropdown @command="handleCommand">
          <span style="cursor: pointer">管理员 <el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main style="background: #f5f5f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <router-view v-else />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { getToken, clearAuth, isTokenExpired } from '@waimai/shared';

const router = useRouter();
const isLoggedIn = computed(() => {
  const t = getToken();
  return !!t && !isTokenExpired(t);
});

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    clearAuth();
    router.replace('/login');
  }
}
</script>

<style>
body {
  margin: 0;
}
.logo {
  color: #fff;
  text-align: center;
  padding: 20px 0;
  font-size: 18px;
  font-weight: 600;
}
</style>
