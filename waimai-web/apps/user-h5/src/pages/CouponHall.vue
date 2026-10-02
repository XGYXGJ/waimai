<template>
  <div class="hall-page">
    <van-nav-bar title="领券大厅" left-arrow @click-left="$router.back()" />
    <div class="hall-list">
      <div class="coupon-card" v-for="c in coupons" :key="c.couponId">
        <div class="coupon-left">
          <div class="coupon-amount" v-if="c.type === 2">{{ (c.discountRate || 0) * 10 }}折</div>
          <div class="coupon-amount" v-else>¥{{ c.discountAmount }}</div>
        </div>
        <div class="coupon-info">
          <div class="coupon-name">{{ c.name }}</div>
          <div class="coupon-merchant">{{ c.merchantName }}</div>
          <div class="coupon-threshold" v-if="c.thresholdAmount > 0">满{{ c.thresholdAmount }}可用</div>
          <div class="coupon-time">有效期至 {{ c.endTime }}</div>
        </div>
        <div class="coupon-action">
          <van-button
            size="small"
            round
            :type="btnType(c)"
            :disabled="!canReceive(c)"
            @click="receive(c)"
          >
            {{ btnText(c) }}
          </van-button>
        </div>
      </div>
      <van-empty v-if="!coupons.length" description="暂无优惠券可领" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { showToast } from 'vant';
import { apiCouponHall, apiReceiveCoupon } from '@/api';

const coupons = ref<any[]>([]);

function canReceive(c: any) {
  return !c.soldOut && !c.reachLimit;
}

function btnText(c: any) {
  if (c.soldOut) return '已抢完';
  if (c.reachLimit) return '已达限领';
  return '立即领取';
}

function btnType(c: any) {
  return canReceive(c) ? 'danger' : 'default';
}

async function receive(c: any) {
  if (!canReceive(c)) return;
  try {
    await apiReceiveCoupon(c.couponId);
    showToast('领取成功');
    load();
  } catch (e: any) {
    showToast(e.message || '领取失败');
  }
}

async function load() {
  try {
    const data: any = await apiCouponHall();
    coupons.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>

<style scoped>
.hall-page {
  background: #f5f5f5;
  min-height: 100vh;
}
.hall-list {
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
  width: 96px;
  background: #ff6034;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}
.coupon-amount {
  font-size: 20px;
  font-weight: bold;
}
.coupon-info {
  flex: 1;
  padding: 12px;
  min-width: 0;
}
.coupon-name {
  font-weight: 600;
}
.coupon-merchant {
  font-size: 12px;
  color: #ff6034;
  margin-top: 4px;
}
.coupon-threshold,
.coupon-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.coupon-action {
  display: flex;
  align-items: center;
  padding: 0 12px;
}
</style>
