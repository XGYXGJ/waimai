<template>
  <el-container v-if="isLoggedIn" style="height: 100vh">
    <el-aside width="200px" style="background: #001529">
      <div class="logo">商户中心</div>
      <el-menu :default-active="$route.path" background-color="#001529" text-color="#a6adb4" active-text-color="#fff" router>
        <el-menu-item index="/"><el-icon><HomeFilled /></el-icon>工作台</el-menu-item>
        <el-menu-item index="/orders"><el-icon><List /></el-icon>订单管理</el-menu-item>
        <el-menu-item index="/dishes"><el-icon><Food /></el-icon>菜品管理</el-menu-item>
        <el-menu-item index="/shop"><el-icon><Shop /></el-icon>店铺设置</el-menu-item>
        <el-menu-item index="/coupons"><el-icon><Ticket /></el-icon>优惠券</el-menu-item>
        <el-menu-item index="/bid"><el-icon><TrendCharts /></el-icon>竞价投放</el-menu-item>
        <el-menu-item index="/forecast"><el-icon><DataAnalysis /></el-icon>销量预测</el-menu-item>
        <el-menu-item index="/reviews"><el-icon><ChatDotRound /></el-icon>评价管理</el-menu-item>
        <el-menu-item index="/im"><el-icon><ChatLineRound /></el-icon>顾客会话</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #eee; display: flex; justify-content: flex-end; align-items: center">
        <el-dropdown @command="handleCommand">
          <span style="cursor: pointer">{{ shopName || '商户' }} <el-icon><ArrowDown /></el-icon></span>
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
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { hasValidSession, clearAuth, get } from '@waimai/shared';

const router = useRouter();
const isLoggedIn = computed(() => hasValidSession());
const shopName = ref('');

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    clearAuth();
    router.replace('/login');
  }
}

onMounted(async () => {
  if (hasValidSession()) {
    try {
      const data: any = await get('/merchant/shop/info');
      shopName.value = data.shopName || '';
    } catch {}
  }
});
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
