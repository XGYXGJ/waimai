<template>
  <el-card>
    <div style="display: flex; gap: 12px; margin-bottom: 16px">
      <el-input v-model="keyword" placeholder="搜索订单号/地址" style="width: 300px" @keyup.enter="search" />
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="search">
        <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-table :data="records">
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="addressSnapshot" label="地址" show-overflow-tooltip />
      <el-table-column prop="payAmount" label="金额" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="!['DELIVERED', 'REFUNDED', 'CANCELLED'].includes(row.status)" size="small" type="danger" @click="refund(row)">退款</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiOrders, apiRefund } from '@/api';

const keyword = ref('');
const status = ref('');
const records = ref<any[]>([]);

const statusMap: Record<string, string> = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ACCEPTED: '备餐中', WAITING_PICKUP: '待取餐',
  DELIVERING: '配送中', DELIVERED: '已送达', CANCELLED: '已取消', REFUNDED: '已退款',
};

async function search() {
  load();
}

async function refund(row: any) {
  await ElMessageBox.confirm('确认退款该订单？', '提示', { type: 'warning' });
  try {
    await apiRefund(row.id);
    ElMessage.success('已退款');
    load();
  } catch (e: any) {
    ElMessage.error(e.message);
  }
}

async function load() {
  try {
    const data: any = await apiOrders(keyword.value, status.value);
    records.value = data.records || [];
  } catch {}
}

onMounted(load);
</script>
