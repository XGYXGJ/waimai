<template>
  <div>
    <el-card>
      <el-tabs v-model="activeStatus" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待接单" name="PAID" />
        <el-tab-pane label="备餐中" name="ACCEPTED" />
        <el-tab-pane label="配送中" name="DELIVERING" />
        <el-tab-pane label="已完成" name="DELIVERED" />
      </el-tabs>
      <el-table :data="orders" v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <!-- 后端给的是 addressText；addressSnapshot 是原始 JSON，绑上去会显示一整段 JSON -->
        <el-table-column prop="addressText" label="地址" show-overflow-tooltip />
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

/** 订单状态文案。必须是函数（el-table 插槽里按 statusText(row.status) 调用）。 */
function statusText(s?: string): string {
  return (
    {
      PENDING_PAYMENT: '待支付', PAID: '待接单', ACCEPTED: '备餐中', WAITING_PICKUP: '待取餐',
      DELIVERING: '配送中', DELIVERED: '已送达', CANCELLED: '已取消', REFUNDED: '已退款',
    }[s || ''] || s || ''
  );
}

async function load() {
  loading.value = true;
  try {
    const data: any = await apiMerchantOrders(activeStatus.value, page.value);
    orders.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    // 原来只有 try/finally 没有 catch：请求失败会变成 unhandled rejection，
    // 表格继续显示上一批陈旧数据，且没有任何提示
    ElMessage.error(e?.message || '订单加载失败');
  } finally {
    loading.value = false;
  }
}

function onPage(p: number) {
  page.value = p;
  load();
}

/**
 * 切换状态筛选：必须回到第 1 页。
 * 原来直接 @tab-change="load"，page 沿用上一页 —— 在「已完成」翻到第 3 页再切到
 * 「待接单」，会请求 ?status=PAID&page=3，表格空白但总数和页码还停在原处。
 */
function onTabChange() {
  page.value = 1;
  load();
}

// 接单/拒单/出餐都可能被服务端拒绝（例如单子已被骑手抢走），
// 不 catch 的话点击后毫无反应，用户会以为按钮坏了
async function accept(id: number) {
  try {
    await apiAcceptOrder(id);
    ElMessage.success('已接单');
  } catch (e: any) {
    ElMessage.error(e?.message || '接单失败');
  }
  load();
}
async function reject(id: number) {
  try {
    await apiRejectOrder(id, '商家拒单');
    ElMessage.success('已拒单');
  } catch (e: any) {
    ElMessage.error(e?.message || '拒单失败');
  }
  load();
}
async function ready(id: number) {
  try {
    await apiReadyOrder(id);
    ElMessage.success('已出餐');
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败');
  }
  load();
}

onMounted(load);
</script>
