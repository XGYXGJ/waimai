<template>
  <div class="hall-page">
    <van-nav-bar title="领券大厅" left-arrow @click-left="$router.back()" />

    <div class="hall-list">
      <div class="hall-tip">
        <van-icon name="coupon-o" size="14" />
        <span>领到的券可在「我的优惠券」中查看与使用</span>
      </div>

      <article
        class="coupon-card"
        v-for="c in coupons"
        :key="c.couponId"
        :class="{ 'coupon-card--off': !canReceive(c) }"
      >
        <div class="coupon-left">
          <div class="coupon-amount" v-if="c.type === 2">
            {{ (c.discountRate || 0) * 10 }}<span class="coupon-unit">折</span>
          </div>
          <div class="coupon-amount" v-else>
            <span class="coupon-unit">¥</span>{{ c.discountAmount }}
          </div>
          <div class="coupon-label" v-if="c.thresholdAmount > 0">满{{ c.thresholdAmount }}可用</div>
          <div class="coupon-label" v-else>无门槛</div>
        </div>

        <div class="coupon-info">
          <h3 class="coupon-name">{{ c.name }}</h3>
          <div class="coupon-merchant" v-if="c.merchantName">
            <van-icon name="shop-o" size="12" />
            <span>{{ c.merchantName }}</span>
          </div>
          <div class="coupon-time">
            <van-icon name="clock-o" size="12" />
            <span>有效期至 {{ c.endTime }}</span>
          </div>
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
      </article>

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
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

.hall-list {
  padding: var(--wm-space-3);
}

.hall-tip {
  display: flex;
  align-items: center;
  gap: var(--wm-space-2);
  margin-bottom: var(--wm-space-3);
  padding: var(--wm-space-3);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

/* ---------------- 优惠券卡片 ---------------- */
.coupon-card {
  display: flex;
  overflow: hidden;
  margin-bottom: var(--wm-space-3);
  background: var(--wm-bg-card);
  border-radius: var(--wm-radius-lg);
  box-shadow: var(--wm-shadow-1);
}

/* 已抢完 / 已达限领：整体降级到次要层级 */
.coupon-card--off {
  opacity: 0.6;
}

.coupon-left {
  flex: 0 0 100px;
  width: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--wm-space-1);
  padding: var(--wm-space-4) var(--wm-space-2);
  background: var(--wm-primary);
  color: #fff;
}

.coupon-card--off .coupon-left {
  background: var(--wm-text-4);
}

.coupon-amount {
  display: flex;
  align-items: baseline;
  gap: 1px;
  font-size: var(--wm-font-2xl);
  font-weight: 600;
  line-height: var(--wm-leading-tight);
  font-variant-numeric: tabular-nums;
}

.coupon-unit {
  font-size: var(--wm-font-sm);
  font-weight: 400;
}

.coupon-label {
  font-size: var(--wm-font-xs);
  opacity: 0.86;
}

.coupon-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--wm-space-2);
  padding: var(--wm-space-4) var(--wm-space-3);
}

.coupon-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-lg);
  font-weight: 600;
  color: var(--wm-text-1);
}

.coupon-card--off .coupon-name {
  color: var(--wm-text-4);
}

.coupon-merchant {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--wm-font-sm);
  color: var(--wm-primary);
}

.coupon-time {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.coupon-card--off .coupon-time,
.coupon-card--off .coupon-merchant {
  color: var(--wm-text-4);
}

.coupon-action {
  display: flex;
  align-items: center;
  padding: 0 var(--wm-space-3);
}

.coupon-action :deep(.van-button) {
  min-height: var(--wm-tap-min);
  padding: 0 var(--wm-space-4);
}
</style>
