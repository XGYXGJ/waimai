<template>
  <div class="profile-page">
    <van-nav-bar title="我的" />

    <!-- 用户信息 -->
    <section class="card user-card">
      <div class="avatar" aria-hidden="true">{{ (user?.nickname || '用')[0] }}</div>
      <div class="user-info">
        <h2 class="user-name">{{ user?.nickname || '未登录' }}</h2>
        <p class="user-phone">
          <van-icon name="phone-o" size="12" />
          <span>{{ user?.phone || '未绑定手机号' }}</span>
        </p>
      </div>
    </section>

    <!-- 常用功能 -->
    <van-cell-group inset class="card menu-card">
      <van-cell title="领券大厅" icon="coupon-o" is-link to="/coupon-hall" />
      <van-cell title="我的订单" icon="orders-o" is-link to="/orders" />
      <van-cell title="我的优惠券" icon="coupon" is-link to="/coupons" />
      <van-cell title="收货地址" icon="location-o" is-link to="/address" />
      <van-cell title="我的收藏" icon="star-o" is-link @click="showFav = true" />
    </van-cell-group>

    <!-- 服务与支持 -->
    <van-cell-group inset class="card menu-card">
      <van-cell title="我的消息" icon="chat-o" is-link to="/im" />
      <van-cell title="我的售后工单" icon="service-o" is-link to="/tickets" />
    </van-cell-group>

    <!-- 退出登录 -->
    <section class="card">
      <van-button class="logout-btn" round block plain type="danger" @click="logout">退出登录</van-button>
    </section>

    <van-popup v-model:show="showFav" position="bottom" class="fav-popup">
      <div class="popup-title">我的收藏</div>
      <van-cell v-for="f in favorites" :key="f.id" :title="f.shopName" @click="$router.push(`/merchant/${f.merchantId}`)" />
      <van-empty v-if="!favorites.length" description="暂无收藏" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { clearAuth } from '@waimai/shared';
import { useUserStore } from '@/stores/user';
import { apiFavorites } from '@/api';

const router = useRouter();
const userStore = useUserStore();
const user = ref<any>(null);
const showFav = ref(false);
const favorites = ref<any[]>([]);

function logout() {
  clearAuth();
  showToast('已退出');
  router.replace('/login');
}

async function loadFav() {
  try {
    const data: any = await apiFavorites();
    favorites.value = data.records || [];
  } catch {}
}

onMounted(async () => {
  await userStore.fetch();
  user.value = userStore.info;
  loadFav();
});
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  /* 底部固定 tabbar（50px）+ 安全区，避免最后一个卡片被压住 */
  padding-bottom: calc(50px + env(safe-area-inset-bottom));
}

/* ---------------- 通用卡片 ---------------- */
.card {
  margin: var(--wm-space-3);
  padding: var(--wm-space-4);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* ---------------- 用户信息 ---------------- */
.user-card {
  display: flex;
  align-items: center;
  gap: var(--wm-space-4);
}

.avatar {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: var(--wm-radius-full);
  background: var(--wm-primary-50);
  color: var(--wm-primary);
  font-size: var(--wm-font-2xl);
  font-weight: 600;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: var(--wm-font-xl);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  color: var(--wm-text-1);
}

.user-phone {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  margin-top: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

/* ---------------- 菜单 ---------------- */
/* 卡片负责外边距与圆角，去掉 Vant 内层缩进，避免卡片套卡片 */
.menu-card :deep(.van-cell) {
  min-height: var(--wm-tap-min);
  padding: var(--wm-space-3) 0;
  align-items: center;
  background: transparent;
  color: var(--wm-text-2);
  font-size: var(--wm-font-md);
}

.menu-card :deep(.van-cell__left-icon) {
  margin-right: var(--wm-space-3);
  color: var(--wm-primary);
}

.menu-card :deep(.van-cell__right-icon) {
  color: var(--wm-text-4);
}

.menu-card :deep(.van-cell::after) {
  left: 0;
  right: 0;
  border-bottom-color: var(--wm-border);
}

.menu-card :deep(.van-cell:last-child::after) {
  display: none;
}

.logout-btn {
  min-height: var(--wm-tap-min);
  font-size: var(--wm-font-lg);
  font-weight: 600;
}

/* ---------------- 收藏弹层 ---------------- */
.fav-popup {
  max-height: 60%;
  background: var(--wm-bg-card);
}

.popup-title {
  padding: var(--wm-space-4);
  text-align: center;
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
  border-bottom: 1px solid var(--wm-border);
}
</style>
