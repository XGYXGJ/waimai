<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card>
          <div class="card-label">{{ card.label }}</div>
          <div class="card-value">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>今日待处理订单</template>
      <el-table :data="pendingOrders" empty-text="暂无待处理订单">
        <el-table-column prop="orderNo" label="订单号" />
        <el-table-column prop="addressSnapshot" label="地址" />
        <el-table-column prop="payAmount" label="金额" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PAID'" size="small" type="primary" @click="accept(row.id)">接单</el-button>
            <el-button v-if="row.status === 'PAID'" size="small" @click="reject(row.id)">拒单</el-button>
            <el-button v-if="row.status === 'ACCEPTED'" size="small" type="success" @click="ready(row.id)">出餐</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiDashboard, apiMerchantOrders, apiAcceptOrder, apiRejectOrder, apiReadyOrder } from '@/api';
import { WsClient } from '@waimai/shared';

const cards = ref([
  { label: '今日营业额', value: '¥0' },
  { label: '今日订单量', value: '0' },
  { label: '热销菜品', value: '-' },
  { label: '店铺评分', value: '-' },
]);
const pendingOrders = ref<any[]>([]);

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

async function loadDashboard() {
  try {
    const data: any = await apiDashboard();
    if (data.todayAmount !== undefined) cards.value[0].value = `¥${data.todayAmount}`;
    if (data.todayOrders !== undefined) cards.value[1].value = String(data.todayOrders);
  } catch {}
}

async function loadOrders() {
  try {
    const data: any = await apiMerchantOrders();
    pendingOrders.value = (data.records || []).filter((o: any) => ['PAID', 'ACCEPTED'].includes(o.status));
  } catch {}
}

async function accept(id: number) {
  await apiAcceptOrder(id);
  ElMessage.success('已接单');
  loadOrders();
}
async function reject(id: number) {
  await apiRejectOrder(id, '商家拒单');
  ElMessage.success('已拒单');
  loadOrders();
}
async function ready(id: number) {
  await apiReadyOrder(id);
  ElMessage.success('已出餐');
  loadOrders();
}

onMounted(() => {
  loadDashboard();
  loadOrders();
  // WebSocket 新订单提醒
  const ws = new WsClient();
  ws.connect();
  ws.on('NEW_ORDER', () => {
    ElMessage.info('您有新的订单！');
    loadOrders();
  });
});
</script>

<style scoped>
.card-label {
  color: #999;
  font-size: 13px;
}
.card-value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 8px;
}
</style>
