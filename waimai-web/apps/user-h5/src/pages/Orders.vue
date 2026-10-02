<template>
  <div class="orders-page">
    <van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" />
    <van-tabs v-model:active="activeTab">
      <van-tab v-for="t in tabs" :key="t.key" :title="t.label" :name="t.key" />
    </van-tabs>

    <van-list v-model:loading="loading" :finished="finished" @load="load" style="margin-top: 8px">
      <div class="order-card" v-for="o in orders" :key="o.id" @click="goDetail(o.id)">
        <div class="order-head">
          <span>订单号 {{ o.orderNo }}</span>
          <span class="status">{{ statusText(o.status) }}</span>
        </div>
        <div class="order-info">
          <div>{{ o.merchantName || '商家' }}</div>
          <div class="order-addr">{{ o.addressSnapshot }}</div>
        </div>
        <div class="order-foot">
          <span class="price">{{ o.payAmount }}</span>
          <div class="actions">
            <van-button v-if="o.status === 'PENDING_PAYMENT'" size="small" type="primary" color="#ff6034" @click.stop="goPay(o.id)">去支付</van-button>
            <van-button v-if="o.status === 'DELIVERING'" size="small" type="primary" color="#ff6034" @click.stop="goTrack(o.id)">追踪骑手</van-button>
            <van-button v-if="o.status === 'DELIVERED' && !o.reviewed" size="small" @click.stop="goReview(o.id)">评价</van-button>
          </div>
        </div>
      </div>
      <van-empty v-if="finished && !orders.length" description="暂无订单" />
    </van-list>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiMyOrders } from '@/api';

const router = useRouter();
const tabs = [
  { key: '', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待支付' },
  { key: 'DELIVERING', label: '进行中' },
  { key: 'DELIVERED', label: '待评价' },
];
const activeTab = ref('');
const orders = ref<any[]>([]);
const loading = ref(false);
const finished = ref(false);
const page = ref(1);

const statusText: Record<string, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '待接单',
  ACCEPTED: '备餐中',
  WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中',
  DELIVERED: '已送达',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
};

function goDetail(id: number) {
  router.push(`/orders/${id}`);
}
function goPay(id: number) {
  router.push(`/pay/${id}`);
}
function goTrack(id: number) {
  router.push(`/orders/${id}/track`);
}
function goReview(id: number) {
  router.push(`/orders/${id}/review`);
}

async function load() {
  loading.value = true;
  try {
    const data: any = await apiMyOrders(activeTab.value, page.value);
    const records = data.records || [];
    orders.value = page.value === 1 ? records : [...orders.value, ...records];
    finished.value = records.length < 10;
    page.value++;
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.order-card {
  background: #fff;
  margin: 8px 12px;
  border-radius: 10px;
  padding: 12px;
}
.order-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #666;
}
.status {
  color: #ff6034;
}
.order-info {
  margin: 8px 0;
}
.order-addr {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>
