<template>
  <div class="pay-page">
    <van-nav-bar title="支付" left-arrow @click-left="$router.back()" />
    <div class="pay-body">
      <div class="amount">
        <div class="label">支付金额</div>
        <div class="value price">{{ amount }}</div>
      </div>
      <van-button round block type="primary" color="#ff6034" :loading="paying" @click="pay">确认支付</van-button>
      <div class="tip">模拟支付，不产生真实扣款</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiOrderDetail, apiOrderPay } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.orderId);
const amount = ref(0);
const paying = ref(false);

async function pay() {
  paying.value = true;
  try {
    await apiOrderPay(orderId);
    showToast('支付成功');
    setTimeout(() => router.replace(`/orders/${orderId}`), 1000);
  } catch (e: any) {
    showToast(e.message);
  } finally {
    paying.value = false;
  }
}

onMounted(async () => {
  try {
    const data: any = await apiOrderDetail(orderId);
    amount.value = data.payAmount || 0;
  } catch {}
});
</script>

<style scoped>
.pay-body {
  padding: 40px 24px;
}
.amount {
  text-align: center;
  margin: 40px 0;
}
.amount .label {
  color: #999;
  font-size: 14px;
}
.amount .value {
  font-size: 40px;
  font-weight: bold;
  margin-top: 12px;
}
.tip {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin-top: 16px;
}
</style>
