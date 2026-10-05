<template>
  <div class="coupons-page">
    <van-nav-bar title="我的优惠券" left-arrow @click-left="$router.back()" />

    <van-tabs v-model:active="active">
      <van-tab title="未使用" name="0" />
      <van-tab title="已使用" name="1" />
      <van-tab title="已过期" name="2" />
    </van-tabs>

    <div class="coupon-list">
      <article
        class="coupon-card"
        v-for="c in coupons"
        :key="c.id"
        :class="{ 'coupon-card--off': active !== '0' }"
      >
        <div class="coupon-left">
          <div class="coupon-amount" v-if="c.discountAmount">
            <span class="coupon-unit">¥</span>{{ c.discountAmount }}
          </div>
          <div class="coupon-amount" v-else>
            {{ (c.discountRate || 0) * 10 }}<span class="coupon-unit">折</span>
          </div>
          <div class="coupon-label" v-if="c.thresholdAmount">满{{ c.thresholdAmount }}可用</div>
          <div class="coupon-label" v-else>无门槛</div>
        </div>

        <div class="coupon-info">
          <div class="coupon-top">
            <h3 class="coupon-name">{{ c.name }}</h3>
            <span class="coupon-state" v-if="active === '1'">
              <van-icon name="checked" size="12" />已使用
            </span>
            <span class="coupon-state" v-else-if="active === '2'">
              <van-icon name="clock-o" size="12" />已过期
            </span>
          </div>
          <div class="coupon-time">
            <van-icon name="clock-o" size="12" />
            <span>有效期至 {{ c.endTime }}</span>
          </div>
        </div>
      </article>

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
.coupons-page {
  min-height: 100vh;
  background: var(--wm-bg-page);
  padding-bottom: var(--wm-space-6);
}

.coupon-list {
  padding: var(--wm-space-3);
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

/* 已使用 / 已过期：整卡降到次要层级并降低不透明度 */
.coupon-card--off {
  opacity: 0.6;
}

.coupon-left {
  flex: 0 0 104px;
  width: 104px;
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
  padding: var(--wm-space-4);
}

.coupon-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wm-space-2);
}

.coupon-name {
  min-width: 0;
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

.coupon-state {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--wm-space-1);
  padding: 1px var(--wm-space-2);
  border: 1px solid var(--wm-border);
  border-radius: var(--wm-radius-full);
  font-size: var(--wm-font-xs);
  color: var(--wm-text-4);
}

.coupon-time {
  display: flex;
  align-items: center;
  gap: var(--wm-space-1);
  font-size: var(--wm-font-sm);
  color: var(--wm-text-3);
}

.coupon-card--off .coupon-time {
  color: var(--wm-text-4);
}
</style>
