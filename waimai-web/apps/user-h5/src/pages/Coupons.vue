<template>
  <div class="coupons-page">
    <van-nav-bar title="我的优惠券" left-arrow @click-left="$router.back()" />
    <van-tabs v-model:active="active">
      <van-tab title="未使用" name="0" />
      <van-tab title="已使用" name="1" />
      <van-tab title="已过期" name="2" />
    </van-tabs>
    <div class="coupon-list">
      <div class="coupon-card" v-for="c in coupons" :key="c.id">
        <div class="coupon-left">
          <div class="coupon-amount" v-if="c.discountAmount">¥{{ c.discountAmount }}</div>
          <div class="coupon-amount" v-else>{{ (c.discountRate || 0) * 10 }}折</div>
        </div>
        <div class="coupon-info">
          <div class="coupon-name">{{ c.name }}</div>
          <div class="coupon-threshold" v-if="c.thresholdAmount">满{{ c.thresholdAmount }}可用</div>
          <div class="coupon-time">{{ c.endTime }}</div>
        </div>
      </div>
      <van-empty v-if="!coupons.length" description="暂无优惠券" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { apiMyCoupons } from '@/api';

const active = ref('0');
const coupons = ref<any[]>([]);

async function load() {
  try {
    const data: any = await apiMyCoupons(Number(active.value));
    coupons.value = data.records || [];
  } catch {}
}

watch(active, load);
onMounted(load);
</script>

<style scoped>
.coupon-list {
  padding: 12px;
}
.coupon-card {
  display: flex;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 10px;
  overflow: hidden;
}
.coupon-left {
  width: 100px;
  background: #ff6034;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}
.coupon-amount {
  font-size: 22px;
  font-weight: bold;
}
.coupon-info {
  flex: 1;
  padding: 12px;
}
.coupon-name {
  font-weight: 600;
}
.coupon-threshold,
.coupon-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
