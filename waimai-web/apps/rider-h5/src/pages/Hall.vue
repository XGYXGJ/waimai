<template>
  <div class="hall">
    <van-nav-bar title="抢单大厅" />
    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="order-card" v-for="o in orders" :key="o.orderId">
        <div class="card-head">
          <span class="order-no">{{ o.orderNo }}</span>
          <span class="amount price">{{ o.payAmount }}</span>
        </div>
        <div class="card-addr">{{ o.address }}</div>
        <div class="card-foot">
          <span class="time">{{ o.createdAt }}</span>
          <van-button size="small" type="primary" color="#07c160" round @click="grab(o.orderId)">抢单</van-button>
        </div>
      </div>
      <van-empty v-if="!orders.length" description="暂无待接订单" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiHall, apiGrab } from '@/api';

const router = useRouter();
const orders = ref<any[]>([]);
const refreshing = ref(false);

async function grab(id: number) {
  try {
    await apiGrab(id);
    showToast('抢单成功');
    router.push(`/map/${id}`);
  } catch (e: any) {
    showToast(e.message);
    load();
  }
}

async function load() {
  try {
    orders.value = (await apiHall()) as any[];
  } catch {}
  refreshing.value = false;
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
  align-items: center;
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
  color: #333;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.time {
  color: #999;
  font-size: 12px;
}
</style>
