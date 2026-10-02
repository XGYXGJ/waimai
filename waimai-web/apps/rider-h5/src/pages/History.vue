<template>
  <div class="history">
    <van-nav-bar title="历史订单" />
    <div class="order-card" v-for="o in orders" :key="o.orderId">
      <div class="card-head">
        <span class="order-no">{{ o.orderNo }}</span>
        <span class="amount price">{{ o.payAmount }}</span>
      </div>
      <div class="card-addr">{{ o.address }}</div>
      <div class="card-foot">
        <van-tag :type="o.status === 'DELIVERED' ? 'success' : 'info'">{{ statusText(o.status) }}</van-tag>
      </div>
    </div>
    <van-empty v-if="!orders.length" description="暂无历史订单" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiDeliveries } from '@/api';

const orders = ref<any[]>([]);

const statusText: Record<string, string> = {
  WAITING_PICKUP: '待取餐', DELIVERING: '配送中', DELIVERED: '已送达',
};

onMounted(async () => {
  try {
    orders.value = (await apiDeliveries()) as any[];
  } catch {}
});
</script>

<style scoped>
.order-card {
  background: #fff;
  margin: 10px 12px;
  border-radius: 10px;
  padding: 14px;
}
.card-head {
  display: flex;
  justify-content: space-between;
}
.order-no {
  color: #666;
  font-size: 13px;
}
.amount {
  font-size: 18px;
}
.card-addr {
  margin: 8px 0;
}
.card-foot {
  display: flex;
  justify-content: flex-end;
}
</style>
