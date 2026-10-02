<template>
  <div>
    <el-card>
      <el-tabs v-model="activeStatus" @tab-change="load">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待接单" name="PAID" />
        <el-tab-pane label="备餐中" name="ACCEPTED" />
        <el-tab-pane label="配送中" name="DELIVERING" />
        <el-tab-pane label="已完成" name="DELIVERED" />
      </el-tabs>
      <el-table :data="orders" v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="addressSnapshot" label="地址" show-overflow-tooltip />
        <el-table-column prop="payAmount" label="金额" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PAID'" size="small" type="primary" @click="accept(row.id)">接单</el-button>
            <el-button v-if="row.status === 'PAID'" size="small" type="danger" @click="reject(row.id)">拒单</el-button>
            <el-button v-if="row.status === 'ACCEPTED'" size="small" type="success" @click="ready(row.id)">出餐</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px" layout="prev, pager, next" :total="total" :page-size="10" @current-change="onPage" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiMerchantOrders, apiAcceptOrder, apiRejectOrder, apiReadyOrder } from '@/api';

const activeStatus = ref('');
const orders = ref<any[]>([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);

const statusText: Record<string, string> = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ACCEPTED: '备餐中', WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中', DELIVERED: '已送达', CANCELLED: '已取消', REFUNDED: '已退款',
};

async function load() {
  loading.value = true;
  try {
    const data: any = await apiMerchantOrders(activeStatus.value, page.value);
    orders.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function onPage(p: number) {
  page.value = p;
  load();
}

async function accept(id: number) {
  await apiAcceptOrder(id);
  ElMessage.success('已接单');
  load();
}
async function reject(id: number) {
  await apiRejectOrder(id, '商家拒单');
  ElMessage.success('已拒单');
  load();
}
async function ready(id: number) {
  await apiReadyOrder(id);
  ElMessage.success('已出餐');
  load();
}

onMounted(load);
</script>
