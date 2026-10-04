<template>
  <div class="profile-page">
    <van-nav-bar title="我的" />
    <div class="profile-header">
      <div class="avatar">{{ (user?.nickname || '用')[0] }}</div>
      <div class="info">
        <div class="name">{{ user?.nickname || '未登录' }}</div>
        <div class="phone">{{ user?.phone }}</div>
      </div>
    </div>

    <van-cell-group inset>
      <van-cell title="领券大厅" is-link to="/coupon-hall" />
      <van-cell title="我的订单" is-link to="/orders" />
      <van-cell title="我的优惠券" is-link to="/coupons" />
      <van-cell title="收货地址" is-link to="/address" />
      <van-cell title="我的收藏" is-link @click="showFav = true" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="我的消息" is-link to="/im" />
      <van-cell title="我的售后工单" is-link to="/tickets" />
    </van-cell-group>

    <div style="margin: 24px 16px">
      <van-button round block plain type="danger" @click="logout">退出登录</van-button>
    </div>

    <van-popup v-model:show="showFav" position="bottom" style="max-height: 60%">
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
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 16px;
  background: linear-gradient(135deg, #ff6034, #ff8a3d);
  color: #fff;
  margin-bottom: 12px;
}
.avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: bold;
}
.name {
  font-size: 18px;
  font-weight: 600;
}
.phone {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 4px;
}
.popup-title {
  text-align: center;
  padding: 16px;
  font-weight: 600;
}
</style>
