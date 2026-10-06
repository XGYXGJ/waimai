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
        <!-- 后端给的是 addressText（拼好的可读文本）；addressSnapshot 是原始 JSON，
             直接绑到表格里会把整段 JSON 显示出来 -->
        <el-table-column prop="addressText" label="地址" show-overflow-tooltip />
        <el-table-column prop="payAmount" label="金额" width="100" />
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
import { ref, onMounted, onUnmounted } from 'vue';
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

/** 订单状态文案。必须是函数（模板插槽里按 statusText(row.status) 调用）。 */
function statusText(s?: string): string {
  return (
    {
      PENDING_PAYMENT: '待支付',
      PAID: '待接单',
      ACCEPTED: '备餐中',
      WAITING_PICKUP: '待取餐',
      DELIVERING: '配送中',
      DELIVERED: '已送达',
      CANCELLED: '已取消',
      REFUNDED: '已退款',
    }[s || ''] || s || ''
  );
}

async function loadDashboard() {
  try {
    const data: any = await apiDashboard();
    // 后端字段名是 todayGmv / todayOrders，原来这里读 todayAmount，条件永远不成立，
    // 「今日营业额」卡一直是 ¥0。
    if (data.todayGmv !== undefined) cards.value[0].value = `¥${Number(data.todayGmv).toFixed(2)}`;
    if (data.todayOrders !== undefined) cards.value[1].value = String(data.todayOrders);
    // 热销菜品：后端返回 [{dishName, count}]，取第一名；没有就保持 '-'
    const hot = Array.isArray(data.hotDishes) ? data.hotDishes[0] : null;
    cards.value[2].value = hot ? `${hot.dishName} (${hot.count})` : '-';
  } catch (e: any) {
    ElMessage.error(e?.message || '工作台数据加载失败');
  }
}

async function loadOrders() {
  try {
    const data: any = await apiMerchantOrders();
    pendingOrders.value = (data.records || []).filter((o: any) => ['PAID', 'ACCEPTED'].includes(o.status));
  } catch {
    // 拉不到就保持空态
  }
}

// 接单/拒单/出餐都可能被服务端拒绝（例如单子已被骑手抢走），
// 不 catch 的话点击后毫无反应，用户会以为按钮坏了。
async function accept(id: number) {
  try {
    await apiAcceptOrder(id);
    ElMessage.success('已接单');
  } catch (e: any) {
    ElMessage.error(e?.message || '接单失败');
  }
  loadOrders();
}
async function reject(id: number) {
  try {
    await apiRejectOrder(id, '商家拒单');
    ElMessage.success('已拒单');
  } catch (e: any) {
    ElMessage.error(e?.message || '拒单失败');
  }
  loadOrders();
}
async function ready(id: number) {
  try {
    await apiReadyOrder(id);
    ElMessage.success('已出餐');
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败');
  }
  loadOrders();
}

let ws: WsClient | null = null;
onMounted(() => {
  loadDashboard();
  loadOrders();
  // WebSocket 新订单提醒
  ws = new WsClient();
  ws.connect();
  ws.on('NEW_ORDER', () => {
    ElMessage.info('您有新的订单！');
    loadOrders();
    loadDashboard();
  });
});
// 不关的话每进一次工作台就多一条活连接 + 一个 30 秒心跳定时器
onUnmounted(() => ws?.close());
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
