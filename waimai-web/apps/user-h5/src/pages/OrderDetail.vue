<template>
  <div class="order-detail">
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />

    <div class="status-card" v-if="order.id">
      <div class="status-text">{{ statusText(order.status) }}</div>
      <div class="status-sub" v-if="order.status === 'DELIVERING' && order.riderName">骑手 {{ order.riderName }} 正在配送</div>
    </div>

    <van-cell-group inset>
      <van-cell title="订单号" :value="order.orderNo" />
      <van-cell title="配送地址" :value="order.addressSnapshot" />
      <van-cell title="备注" :value="order.remark || '无'" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="商品金额" :value="`¥${order.dishAmount || 0}`" />
      <van-cell title="配送费" :value="`¥${order.deliveryFee || 0}`" />
      <van-cell title="优惠" :value="`-¥${order.discountAmount || 0}`" />
      <van-cell title="实付" :value="`¥${order.payAmount || 0}`" />
    </van-cell-group>

    <div class="actions" v-if="order.status === 'PENDING_PAYMENT'">
      <van-button round block type="primary" color="#ff6034" @click="pay">去支付</van-button>
      <van-button round block plain style="margin-top: 8px" @click="cancel">取消订单</van-button>
    </div>
    <div class="actions" v-if="order.status === 'DELIVERING'">
      <van-button round block type="primary" color="#ff6034" @click="track">追踪骑手</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { apiOrderDetail, apiOrderPay, apiOrderCancel } from '@/api';

const route = useRoute();
const router = useRouter();
const orderId = Number(route.params.id);
const order = ref<any>({});

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

async function pay() {
  try {
    await apiOrderPay(orderId);
    showToast('支付成功');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

async function cancel() {
  try {
    await apiOrderCancel(orderId);
    showToast('已取消');
    load();
  } catch (e: any) {
    showToast(e.message);
  }
}

function track() {
  router.push(`/orders/${orderId}/track`);
}

async function load() {
  try {
    order.value = await apiOrderDetail(orderId);
  } catch {}
}

onMounted(load);
</script>

<style scoped>
.status-card {
  background: linear-gradient(135deg, #ff6034, #ff8a3d);
  color: #fff;
  padding: 24px 16px;
  margin-bottom: 12px;
}
.status-text {
  font-size: 20px;
  font-weight: 600;
}
.status-sub {
  font-size: 13px;
  margin-top: 8px;
  opacity: 0.9;
}
.actions {
  margin: 20px 16px;
}
</style>
