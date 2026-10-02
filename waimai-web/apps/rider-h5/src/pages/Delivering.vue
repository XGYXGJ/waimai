<template>
  <div class="delivering">
    <van-nav-bar title="进行中订单" />
    <div class="order-card" v-for="o in orders" :key="o.orderId">
      <div class="card-head">
        <span class="order-no">{{ o.orderNo }}</span>
        <span class="amount price">{{ o.payAmount }}</span>
      </div>
      <div class="card-addr">{{ o.address }}</div>
      <div class="card-foot">
        <van-tag type="warning">{{ statusText(o.status) }}</van-tag>
        <div>
          <van-button v-if="o.status === 'WAITING_PICKUP'" size="small" type="primary" color="#07c160" @click="pickup(o.orderId)">确认取餐</van-button>
          <van-button v-if="o.status === 'DELIVERING'" size="small" type="success" color="#07c160" @click="deliver(o.orderId)">确认送达</van-button>
        </div>
      </div>
    </div>
    <van-empty v-if="!orders.length" description="暂无进行中订单" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { showToast } from 'vant';
import { apiDeliveries, apiPickup, apiDeliver } from '@/api';

const orders = ref<any[]>([]);

const statusText: Record<string, string> = {
  WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中',
  DELIVERED: '已送达',
};

async function pickup(id: number) {
  try {
    await apiPickup(id);
    showToast('已取餐');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function deliver(id: number) {
  try {
    await apiDeliver(id);
    showToast('已送达');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function load() {
  try {
    orders.value = (await apiDeliveries()) as any[];
  } catch {}
}

onMounted(load);
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
  justify-content: space-between;
  align-items: center;
}
</style>
