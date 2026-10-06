<template>
  <div class="history">
    <van-nav-bar title="历史订单">
      <template #right>
        <van-icon name="service-o" size="18" @click="router.push('/me')" />
      </template>
    </van-nav-bar>
    <div class="order-card" v-for="o in orders" :key="o.orderId">
      <div class="card-head">
        <span class="shop-name">{{ o.merchantName || '未知商家' }}</span>
        <span class="order-no">{{ o.orderNo }}</span>
      </div>

      <div class="card-addr">
        <div class="addr-line">
          <van-icon name="location-o" class="ico" />
          <span class="addr-text">{{ o.addressText || '未填写收货地址' }}</span>
        </div>
        <div class="addr-line" v-if="o.contact || o.phone">
          <van-icon name="phone-o" class="ico" />
          <span class="addr-text">{{ o.contact || '匿名' }} {{ o.phone }}</span>
        </div>
      </div>

      <div class="fee-row">
        <div class="fee">
          <span class="fee-label">配送费</span>
          <span class="fee-value">{{ formatMoney(o.deliveryFee) }}</span>
        </div>
        <div class="fee">
          <span class="fee-label">配送里程</span>
          <span class="fee-value">{{ o.distanceKm == null ? '—' : formatDistance(o.distanceKm) }}</span>
        </div>
        <div class="fee income">
          <span class="fee-label">本单到手</span>
          <span class="fee-value">{{ formatMoney(o.riderIncome) }}</span>
        </div>
      </div>

      <div class="card-foot">
        <span class="time">{{ formatDateTime(o.deliveredTime || o.createdAt) }}</span>
        <!-- Vant 4 只有 default/primary/success/danger/warning，没有 info -->
        <van-tag :type="o.status === 'DELIVERED' ? 'success' : 'default'">
          {{ statusText(o.status) }}
        </van-tag>
      </div>
    </div>
    <van-empty v-if="!orders.length && !loadFailed" description="暂无历史订单" />
    <div v-if="loadFailed" class="load-failed">
      <van-empty :description="failMsg" />
      <van-button type="primary" round size="small" @click="load">重新加载</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { apiHistory } from '@/api';
import { formatDistance, formatDateTime, formatMoney } from '@waimai/shared';

const router = useRouter();
const orders = ref<any[]>([]);
const loadFailed = ref(false);
const failMsg = ref('加载失败');

/** 订单状态文案。必须是函数（模板按 statusText(o.status) 调用）；
 *  写成对象字面量会在 v-for 渲染时抛 "statusText is not a function"。 */
function statusText(s?: string): string {
  return (
    {
      WAITING_PICKUP: '待取餐',
      DELIVERING: '配送中',
      DELIVERED: '已送达',
    }[s || ''] || s || ''
  );
}

async function load() {
  loadFailed.value = false;
  try {
    // 专用历史接口：原来这里调的是 /rider/deliveries，和「进行中」拿到的是同一份数据
    orders.value = (await apiHistory()) as any[];
  } catch (e: any) {
    loadFailed.value = true;
    failMsg.value = e?.message || '加载失败，请确认后端已启动';
  }
}

onMounted(load);
</script>

<style scoped>
/* tabbar 是 fixed 的：页面必须留出它的高度 + 安全区 */
.history {
  min-height: 100vh;
  padding-bottom: calc(60px + env(safe-area-inset-bottom));
  background: #f5f5f5;
}
.order-card {
  background: #fff;
  margin: 10px 12px;
  border-radius: 10px;
  padding: 14px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.shop-name {
  font-size: 15px;
  font-weight: 600;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-no {
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}

.card-addr {
  margin: 10px 0;
}
.addr-line {
  display: flex;
  gap: 4px;
  color: #333;
  font-size: 13px;
  line-height: 1.5;
}
.addr-line + .addr-line {
  margin-top: 2px;
}
.ico {
  flex-shrink: 0;
  margin-top: 2px;
  color: #999;
}
.addr-text {
  word-break: break-all;
}

.fee-row {
  display: flex;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 8px 10px;
}
.fee {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fee + .fee {
  border-left: 1px solid #ebedf0;
  padding-left: 10px;
}
.fee-label {
  font-size: 11px;
  color: #999;
}
.fee-value {
  font-size: 14px;
  font-weight: 600;
  color: #222;
}
.fee.income .fee-value {
  color: #07c160;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.time {
  color: #999;
  font-size: 12px;
}

.load-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
}
</style>
